// macremoted：在 Mac 上收手机发来的 UDP 指令，模拟键盘/鼠标/媒体键。
// 编译：见 build.sh（x86_64，兼容老 Intel Mac）。
// 协议（UDP 47800，一个包一条指令，UTF-8 文本）：
//   ping                  -> 回 "pong <电脑名>"（也用于局域网广播发现）
//   m <dx> <dy>           鼠标相对移动（像素，可为小数）
//   c l|r|d               左键单击 / 右键单击 / 左键双击
//   b down|up             左键按下 / 松开（拖动用）
//   s <dy> [dx]           滚轮（行数，正数向上）
//   k <组合键>            如 space、right、shift+n、cmd+w、bracketright
//   t <文字>              输入一段文字（支持中文）
//   v up|down|mute        系统音量（媒体键，会显示音量浮窗）
// 需要在 系统设置 → 隐私与安全性 → 辅助功能 里允许本程序。

#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <ApplicationServices/ApplicationServices.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <IOKit/hidsystem/ev_keymap.h>

#define PORT 47800

static bool gLeftDown = false;

static int keycodeFor(NSString *k) {
    static NSDictionary *map;
    if (!map) {
        map = @{
            @"a":@0, @"s":@1, @"d":@2, @"f":@3, @"h":@4, @"g":@5, @"z":@6, @"x":@7, @"c":@8, @"v":@9,
            @"b":@11, @"q":@12, @"w":@13, @"e":@14, @"r":@15, @"y":@16, @"t":@17,
            @"1":@18, @"2":@19, @"3":@20, @"4":@21, @"6":@22, @"5":@23, @"equal":@24, @"9":@25, @"7":@26,
            @"minus":@27, @"8":@28, @"0":@29, @"bracketright":@30, @"o":@31, @"u":@32, @"bracketleft":@33,
            @"i":@34, @"p":@35, @"return":@36, @"enter":@36, @"l":@37, @"j":@38, @"quote":@39, @"k":@40,
            @"semicolon":@41, @"backslash":@42, @"comma":@43, @"slash":@44, @"n":@45, @"m":@46, @"period":@47,
            @"tab":@48, @"space":@49, @"grave":@50, @"delete":@51, @"backspace":@51, @"escape":@53, @"esc":@53,
            @"f1":@122, @"f2":@120, @"f3":@99, @"f4":@118, @"f5":@96, @"f6":@97, @"f7":@98, @"f8":@100,
            @"f9":@101, @"f10":@109, @"f11":@103, @"f12":@111,
            @"home":@115, @"pageup":@116, @"forwarddelete":@117, @"end":@119, @"pagedown":@121,
            @"left":@123, @"right":@124, @"down":@125, @"up":@126,
        };
    }
    NSNumber *n = map[k.lowercaseString];
    return n ? n.intValue : -1;
}

static void pressKey(NSString *combo) {
    NSArray *parts = [combo componentsSeparatedByString:@"+"];
    CGEventFlags flags = 0;
    int code = -1;
    for (NSString *raw in parts) {
        NSString *p = raw.lowercaseString;
        if ([p isEqual:@"cmd"] || [p isEqual:@"command"]) flags |= kCGEventFlagMaskCommand;
        else if ([p isEqual:@"shift"]) flags |= kCGEventFlagMaskShift;
        else if ([p isEqual:@"alt"] || [p isEqual:@"option"] || [p isEqual:@"opt"]) flags |= kCGEventFlagMaskAlternate;
        else if ([p isEqual:@"ctrl"] || [p isEqual:@"control"]) flags |= kCGEventFlagMaskControl;
        else code = keycodeFor(p);
    }
    if (code < 0) { NSLog(@"未知按键: %@", combo); return; }
    CGEventRef down = CGEventCreateKeyboardEvent(NULL, (CGKeyCode)code, true);
    CGEventRef up = CGEventCreateKeyboardEvent(NULL, (CGKeyCode)code, false);
    // 方向键等需要带 Fn/小键盘标志，否则部分网页不认
    if (code >= 123 && code <= 126) flags |= kCGEventFlagMaskNumericPad | kCGEventFlagMaskSecondaryFn;
    CGEventSetFlags(down, flags);
    CGEventSetFlags(up, flags);
    CGEventPost(kCGHIDEventTap, down);
    usleep(8000);
    CGEventPost(kCGHIDEventTap, up);
    CFRelease(down); CFRelease(up);
}

static void typeString(NSString *text) {
    // 每次最多 20 个 UTF-16 单元，分段发送
    NSUInteger len = text.length;
    for (NSUInteger i = 0; i < len; i += 20) {
        NSUInteger n = MIN(20, len - i);
        unichar buf[20];
        [text getCharacters:buf range:NSMakeRange(i, n)];
        CGEventRef down = CGEventCreateKeyboardEvent(NULL, 0, true);
        CGEventRef up = CGEventCreateKeyboardEvent(NULL, 0, false);
        CGEventKeyboardSetUnicodeString(down, n, buf);
        CGEventKeyboardSetUnicodeString(up, n, buf);
        CGEventPost(kCGHIDEventTap, down);
        CGEventPost(kCGHIDEventTap, up);
        CFRelease(down); CFRelease(up);
        usleep(10000);
    }
}

static CGPoint cursorPos(void) {
    CGEventRef e = CGEventCreate(NULL);
    CGPoint p = CGEventGetLocation(e);
    CFRelease(e);
    return p;
}

static CGRect allDisplaysBounds(void) {
    CGDirectDisplayID ids[8];
    uint32_t cnt = 0;
    CGGetActiveDisplayList(8, ids, &cnt);
    CGRect r = CGRectNull;
    for (uint32_t i = 0; i < cnt; i++) r = CGRectUnion(r, CGDisplayBounds(ids[i]));
    if (CGRectIsNull(r)) r = CGRectMake(0, 0, 1920, 1080);
    return r;
}

static void moveMouse(double dx, double dy) {
    CGPoint p = cursorPos();
    CGRect b = allDisplaysBounds();
    p.x = fmin(fmax(p.x + dx, CGRectGetMinX(b)), CGRectGetMaxX(b) - 1);
    p.y = fmin(fmax(p.y + dy, CGRectGetMinY(b)), CGRectGetMaxY(b) - 1);
    CGEventType t = gLeftDown ? kCGEventLeftMouseDragged : kCGEventMouseMoved;
    CGEventRef e = CGEventCreateMouseEvent(NULL, t, p, kCGMouseButtonLeft);
    CGEventSetIntegerValueField(e, kCGMouseEventDeltaX, (int64_t)dx);
    CGEventSetIntegerValueField(e, kCGMouseEventDeltaY, (int64_t)dy);
    CGEventPost(kCGHIDEventTap, e);
    CFRelease(e);
}

static void mouseButton(CGEventType type, CGMouseButton btn, int clickState) {
    CGEventRef e = CGEventCreateMouseEvent(NULL, type, cursorPos(), btn);
    CGEventSetIntegerValueField(e, kCGMouseEventClickState, clickState);
    CGEventPost(kCGHIDEventTap, e);
    CFRelease(e);
}

static void click(NSString *which) {
    if ([which isEqual:@"r"]) {
        mouseButton(kCGEventRightMouseDown, kCGMouseButtonRight, 1);
        mouseButton(kCGEventRightMouseUp, kCGMouseButtonRight, 1);
    } else if ([which isEqual:@"d"]) {
        mouseButton(kCGEventLeftMouseDown, kCGMouseButtonLeft, 1);
        mouseButton(kCGEventLeftMouseUp, kCGMouseButtonLeft, 1);
        usleep(30000);
        mouseButton(kCGEventLeftMouseDown, kCGMouseButtonLeft, 2);
        mouseButton(kCGEventLeftMouseUp, kCGMouseButtonLeft, 2);
    } else {
        // 连续快速点击时累加 clickState，Mac 才会识别成双击/三击
        static CFAbsoluteTime last = 0;
        static int count = 0;
        CFAbsoluteTime now = CFAbsoluteTimeGetCurrent();
        count = (now - last < 0.45) ? MIN(count + 1, 3) : 1;
        last = now;
        mouseButton(kCGEventLeftMouseDown, kCGMouseButtonLeft, count);
        mouseButton(kCGEventLeftMouseUp, kCGMouseButtonLeft, count);
    }
}

static void scroll(int dy, int dx) {
    CGEventRef e = CGEventCreateScrollWheelEvent(NULL, kCGScrollEventUnitLine, 2, dy, dx);
    CGEventPost(kCGHIDEventTap, e);
    CFRelease(e);
}

static void mediaKey(int key) {
    for (int down = 1; down >= 0; down--) {
        NSInteger data1 = (key << 16) | ((down ? 0xa : 0xb) << 8);
        NSEvent *ev = [NSEvent otherEventWithType:NSEventTypeSystemDefined
                                         location:NSZeroPoint
                                    modifierFlags:(down ? 0xa00 : 0xb00)
                                        timestamp:0
                                     windowNumber:0
                                          context:nil
                                          subtype:8
                                            data1:data1
                                            data2:-1];
        CGEventPost(kCGHIDEventTap, ev.CGEvent);
    }
}

static NSString *handle(NSString *line) {
    NSRange sp = [line rangeOfString:@" "];
    NSString *cmd = sp.location == NSNotFound ? line : [line substringToIndex:sp.location];
    NSString *arg = sp.location == NSNotFound ? @"" : [line substringFromIndex:sp.location + 1];
    NSArray *a = [arg componentsSeparatedByString:@" "];

    if ([cmd isEqual:@"ping"]) {
        NSString *name = [[NSHost currentHost] localizedName] ?: @"Mac";
        return [NSString stringWithFormat:@"pong %@", name];
    } else if ([cmd isEqual:@"m"] && a.count >= 2) {
        moveMouse([a[0] doubleValue], [a[1] doubleValue]);
    } else if ([cmd isEqual:@"c"]) {
        click(arg);
    } else if ([cmd isEqual:@"b"]) {
        if ([arg isEqual:@"down"]) { gLeftDown = true; mouseButton(kCGEventLeftMouseDown, kCGMouseButtonLeft, 1); }
        else { gLeftDown = false; mouseButton(kCGEventLeftMouseUp, kCGMouseButtonLeft, 1); }
    } else if ([cmd isEqual:@"s"]) {
        scroll([a[0] intValue], a.count > 1 ? [a[1] intValue] : 0);
    } else if ([cmd isEqual:@"k"]) {
        pressKey(arg);
    } else if ([cmd isEqual:@"t"]) {
        typeString(arg);
    } else if ([cmd isEqual:@"v"]) {
        if ([arg isEqual:@"up"]) mediaKey(NX_KEYTYPE_SOUND_UP);
        else if ([arg isEqual:@"down"]) mediaKey(NX_KEYTYPE_SOUND_DOWN);
        else if ([arg isEqual:@"mute"]) mediaKey(NX_KEYTYPE_MUTE);
    }
    return nil;
}

int main(int argc, const char *argv[]) {
    @autoreleasepool {
        // 首次运行时弹出辅助功能授权提示
        NSDictionary *opts = @{(__bridge NSString *)kAXTrustedCheckOptionPrompt: @YES};
        if (!AXIsProcessTrustedWithOptions((__bridge CFDictionaryRef)opts)) {
            NSLog(@"还没有辅助功能权限：系统设置 → 隐私与安全性 → 辅助功能，允许 macremoted");
        }

        int fd = socket(AF_INET, SOCK_DGRAM, 0);
        int yes = 1;
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &yes, sizeof yes);
        struct sockaddr_in addr = {0};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(PORT);
        addr.sin_addr.s_addr = htonl(INADDR_ANY);
        if (bind(fd, (struct sockaddr *)&addr, sizeof addr) < 0) { perror("bind"); return 1; }
        NSLog(@"macremoted 监听 UDP %d", PORT);

        char buf[2048];
        for (;;) {
            struct sockaddr_in from; socklen_t fl = sizeof from;
            ssize_t n = recvfrom(fd, buf, sizeof buf - 1, 0, (struct sockaddr *)&from, &fl);
            if (n <= 0) continue;
            buf[n] = 0;
            @autoreleasepool {
                NSString *line = [[NSString alloc] initWithBytes:buf length:n encoding:NSUTF8StringEncoding];
                line = [line stringByTrimmingCharactersInSet:[NSCharacterSet newlineCharacterSet]];
                if (!line.length) continue;
                NSString *reply = handle(line);
                if (reply) {
                    NSData *d = [reply dataUsingEncoding:NSUTF8StringEncoding];
                    sendto(fd, d.bytes, d.length, 0, (struct sockaddr *)&from, fl);
                }
            }
        }
    }
}
