package com.local.floatwin;

import android.content.Context;

/**
 * MIUI 的「一键清理」会直接 kill 掉本应用进程（即使有前台服务）。
 * 这里用 root 起一个独立的小守护进程：进程没了就把它拉起来。
 * 守护进程跑在 uid 0，不受应用进程清理影响；用户关闭悬浮球时删除 flag 让它退出。
 */
public final class Watchdog {

    private static final String FLAG = "/data/local/tmp/.floatwin_watch.on";
    private static final String SCRIPT = "/data/local/tmp/.floatwin_watch.sh";
    private static final String LOCK = "/data/local/tmp/.floatwin_watch.lock";

    private Watchdog() {
    }

    private static final String BODY =
            "FLAG=" + FLAG + "\n"
                    + "LOCK=" + LOCK + "\n"
                    + "if [ -d \"$LOCK\" ]; then\n"
                    + "  P=$(cat \"$LOCK/pid\" 2>/dev/null)\n"
                    + "  if [ -n \"$P\" ] && kill -0 \"$P\" 2>/dev/null; then exit 0; fi\n"
                    + "  rm -rf \"$LOCK\"\n"
                    + "fi\n"
                    + "mkdir -p \"$LOCK\" 2>/dev/null || exit 0\n"
                    + "echo $$ > \"$LOCK/pid\"\n"
                    + "trap 'rm -rf \"$LOCK\"' EXIT INT TERM\n"
                    + "while [ -f \"$FLAG\" ]; do\n"
                    + "  if ! pidof com.local.floatwin >/dev/null 2>&1; then\n"
                    + "    am start-foreground-service -n com.local.floatwin/.BubbleService >/dev/null 2>&1\n"
                    + "    sleep 3\n"
                    + "  fi\n"
                    + "  sleep 4\n"
                    + "done\n"
                    + "rm -rf \"$LOCK\"\n";

    public static void start(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String cmd = "cat > " + SCRIPT + " <<'EOF'\n" + BODY + "EOF\n"
                        + "chmod 755 " + SCRIPT + "\n"
                        + "touch " + FLAG + "\n"
                        + "setsid sh " + SCRIPT + " >/dev/null 2>&1 < /dev/null &\n";
                Shell.Result r = Shell.su(cmd, 9000);
                android.util.Log.d("floatwin", "watchdog start rc=" + r.code);
            }
        }, "floatwin-watchdog").start();
    }

    public static void stop(final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Shell.su("rm -f " + FLAG + "\n"
                        + "P=$(cat " + LOCK + "/pid 2>/dev/null)\n"
                        + "[ -n \"$P\" ] && kill \"$P\" 2>/dev/null\n"
                        + "rm -rf " + LOCK + "\n", 6000);
            }
        }, "floatwin-watchdog-stop").start();
    }
}
