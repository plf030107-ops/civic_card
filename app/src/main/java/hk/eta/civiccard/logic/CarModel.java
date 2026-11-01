package hk.eta.civiccard.logic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import hk.eta.civiccard.config.ConfigStore;

/**
 * 车模图管理。
 *
 * 图片来源：乘趣 /v5/detail 返回的 vehicleModelPicture 字段
 *   例 https://p1.nokeeu.com/carmodel/30ce0b627f0141d8be2932172658b97d.png
 *
 * hook 侧把 URL 写进 model_url.txt，卡片侧据此下载并缓存到本地。
 * 缓存后不再重复下载。
 */
public final class CarModel {

    private static final String URL_FILE = "/data/local/tmp/civic_card/model_url.txt";
    private static final String CACHE_NAME = "car_model.png";

    private CarModel() {}

    /** 取车模图；有缓存用缓存，否则按 URL 下载。无图返回 null */
    public static Bitmap load(Context ctx) {
        // 1. 优先用用户自定义背景
        // （自定义背景在 CardActivity 里单独处理，这里只管车模）

        File cache = new File(ctx.getFilesDir(), CACHE_NAME);
        String url = BridgeReader.readAllText(URL_FILE);

        // URL 变了就重新下载
        String cachedUrl = ConfigStore.getModelUrl(ctx);
        if (cache.exists() && url != null && url.equals(cachedUrl)) {
            return decode(cache);
        }

        if (url == null || url.isEmpty()) {
            return cache.exists() ? decode(cache) : null;
        }

        // 下载
        Bitmap bmp = download(url);
        if (bmp != null) {
            save(cache, bmp);
            ConfigStore.setModelUrl(ctx, url);
            return bmp;
        }

        // 下载失败退回缓存
        return cache.exists() ? decode(cache) : null;
    }

    private static Bitmap decode(File f) {
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void save(File f, Bitmap bmp) {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
        } catch (Throwable ignored) {
        } finally {
            if (fos != null) try { fos.close(); } catch (Throwable ignored) { }
        }
    }

    private static Bitmap download(String urlStr) {
        HttpURLConnection conn = null;
        InputStream is = null;
        try {
            URL u = new URL(urlStr);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(15000);
            conn.setDoInput(true);
            conn.connect();
            if (conn.getResponseCode() != 200) return null;
            is = conn.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            return bmp;
        } catch (Throwable t) {
            return null;
        } finally {
            if (is != null) try { is.close(); } catch (Throwable ignored) { }
            if (conn != null) conn.disconnect();
        }
    }
}
