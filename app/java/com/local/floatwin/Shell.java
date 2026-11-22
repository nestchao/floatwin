package com.local.floatwin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal "su" runner. Feeds the command through stdin so we never have to quote anything,
 * then reads the merged output with a hard timeout.
 */
public final class Shell {

    public static final class Result {
        public final int code;
        public final String out;

        Result(int code, String out) {
            this.code = code;
            this.out = out == null ? "" : out;
        }
    }

    private static final Pattern RC = Pattern.compile("__RC=(-?\\d+)");

    /** Different places a working su binary might live. */
    private static final String[] SU_PATHS = {
            "/system/bin/su",
            "/data/local/tmp/su",
            "su"
    };

    private Shell() {
    }

    public static Result sh(String command, int timeoutMs) {
        try {
            Process p = new ProcessBuilder("/system/bin/sh", "-c", command)
                    .redirectErrorStream(true).start();
            StringBuilder out = pump(p, timeoutMs);
            return new Result(p.exitValue(), out.toString().trim());
        } catch (Exception e) {
            android.util.Log.d("floatwin", "sh failed: " + e);
            return new Result(-1, "sh 失败：" + e.getMessage());
        }
    }

    public static Result su(String command, int timeoutMs) {
        StringBuilder errors = new StringBuilder();
        for (String path : SU_PATHS) {
            try {
                Process process = new ProcessBuilder(path).redirectErrorStream(true).start();
                OutputStream in = process.getOutputStream();
                in.write((command + "\n").getBytes("UTF-8"));
                in.write("echo __RC=$?\n".getBytes("UTF-8"));
                in.write("exit\n".getBytes("UTF-8"));
                in.flush();

                StringBuilder buffer = pump(process, timeoutMs);
                String out = buffer.toString();
                int rc = -1;
                Matcher m = RC.matcher(out);
                if (m.find()) {
                    try {
                        rc = Integer.parseInt(m.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
                out = RC.matcher(out).replaceAll("").trim();
                return new Result(rc, out);
            } catch (IOException e) {
                errors.append(path).append(": ").append(e.getMessage()).append("; ");
            } catch (Exception e) {
                errors.append(path).append(": ").append(e.getMessage()).append("; ");
            }
        }
        android.util.Log.d("floatwin", "su all failed: " + errors);
        return new Result(-1, "无法执行 su（已加白名单后重试）：" + errors);
    }

    private static StringBuilder pump(Process process, int timeoutMs) throws Exception {
        final StringBuilder buffer = new StringBuilder();
        final BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
        Thread pump = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                    // process died
                }
            }
        });
        pump.setDaemon(true);
        pump.start();

        boolean finished = process.waitFor(Math.max(1, timeoutMs),
                java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            pump.join(400);
            throw new IOException("su 超时（可能还在等待授权）");
        }
        pump.join(400);
        return buffer;
    }
}
