package hk.eta.civiccard.logic;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 生成 REAREye 可导入的卡片包（MAML zip）。
 *
 * 包结构（与 REAREye 的 business_*.zip 一致）：
 *   manifest.xml
 *   background.png / tile.png / 图标 *.webp / car_image.png
 *
 * 资源从 assets/cardpack/ 读取，打包到 App 私有目录后交给 REAREye 导入。
 */
public final class CardPackBuilder {

    private static final String ASSET_DIR = "cardpack";

    private CardPackBuilder() {}

    /** 打包并返回 zip 路径，失败返回 null */
    public static File build(Context ctx) {
        File out = new File(ctx.getFilesDir(), "civic_card_pack.zip");
        ZipOutputStream zos = null;
        try {
            String[] names = ctx.getAssets().list(ASSET_DIR);
            if (names == null || names.length == 0) return null;

            zos = new ZipOutputStream(new FileOutputStream(out));
            for (String name : names) {
                InputStream is = null;
                try {
                    is = ctx.getAssets().open(ASSET_DIR + "/" + name);
                    zos.putNextEntry(new ZipEntry(name));
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        zos.write(buf, 0, n);
                    }
                    zos.closeEntry();
                } finally {
                    if (is != null) try { is.close(); } catch (IOException ignored) { }
                }
            }
            zos.finish();
            return out;
        } catch (Throwable t) {
            return null;
        } finally {
            if (zos != null) try { zos.close(); } catch (IOException ignored) { }
        }
    }

    /** 导出卡片包：先生成到私有目录，再尝试复制到外部；外部不可写则留在私有目录 */
    public static File exportToPublic(Context ctx) {
        File src = build(ctx);
        if (src == null || !src.exists()) return null;

        // 优先外部 Download
        File dir = new File("/storage/emulated/0/Download");
        if (dir.exists() && dir.canWrite()) {
            File dst = new File(dir, "乘趣控车卡片.zip");
            if (copy(src, dst)) return dst;
        }

        // 退回 App 外部私有目录（同样能被文件管理器访问）
        File ext = ctx.getExternalFilesDir(null);
        if (ext != null) {
            File dst = new File(ext, "乘趣控车卡片.zip");
            if (copy(src, dst)) return dst;
        }

        return src;
    }

    private static boolean copy(File src, File dst) {
        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            fis = new FileInputStream(src);
            fos = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) fos.write(buf, 0, n);
            fos.flush();
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (fis != null) try { fis.close(); } catch (IOException ignored) { }
            if (fos != null) try { fos.close(); } catch (IOException ignored) { }
        }
    }
}
