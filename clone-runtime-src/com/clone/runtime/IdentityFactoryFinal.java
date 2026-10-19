package com.clone.runtime;

import android.app.Application;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Clone ke andar chalne wala chhota sa runtime.
 * Class ka poora naam jaan boojh kar "androidx.core.app.CoreComponentFactory"
 * ke barabar (38 characters) rakha gaya hai, taaki Binary Manifest mein
 * usi jaga par safely replace ho jaye.
 *
 * Kaam: app start honay se pehle assets/clone_identity.json parh kar
 * Build.* (Manufacturer, Model, Brand, Device, Fingerprint...) ko
 * clone ke liye select kiye gaye Virtual Device par set karna.
 * Koi bhi ghalti ho to khamoshi se ignore - clone har haal mein chale.
 */
public class IdentityFactoryFinal extends androidx.core.app.CoreComponentFactory {
    private static volatile boolean installed = false;

    public IdentityFactoryFinal() {
        install();
    }

    @Override
    public Application instantiateApplication(ClassLoader cl, String className)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        install();
        return super.instantiateApplication(cl, className);
    }

    private static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            String jsonText = readIdentityJson();
            if (jsonText == null) return;
            JSONObject root = new JSONObject(jsonText);
            JSONObject d = root.optJSONObject("device");
            if (d == null) return;

            setBuildField("MANUFACTURER", d.optString("manufacturer"));
            setBuildField("BRAND", d.optString("brand"));
            setBuildField("MODEL", d.optString("model"));
            setBuildField("DEVICE", d.optString("device"));
            setBuildField("PRODUCT", d.optString("product"));
            setBuildField("BOARD", d.optString("device"));
            setBuildField("HARDWARE", d.optString("hardware"));
            setBuildField("FINGERPRINT", d.optString("fingerprint"));
            setBuildField("DISPLAY", d.optString("fingerprint"));
            setBuildField("ID", d.optString("buildId"));
            setBuildField("SERIAL", d.optString("runtimeSerial"));
            setVersionField("RELEASE", d.optString("androidRelease"));
        } catch (Throwable ignored) {
            // Clone ko kabhi crash na karo
        }
    }

    private static String readIdentityJson() {
        InputStream in = null;
        try {
            ClassLoader cl = IdentityFactoryFinal.class.getClassLoader();
            if (cl != null) in = cl.getResourceAsStream("assets/clone_identity.json");
            if (in == null) {
                ClassLoader ctx = Thread.currentThread().getContextClassLoader();
                if (ctx != null) in = ctx.getResourceAsStream("assets/clone_identity.json");
            }
            if (in == null) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) != -1) buf.write(tmp, 0, n);
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
        }
    }

    private static void setBuildField(String name, String value) {
        if (value == null || value.length() == 0) return;
        setStaticString(Build.class, name, value);
    }

    private static void setVersionField(String name, String value) {
        if (value == null || value.length() == 0) return;
        setStaticString(Build.VERSION.class, name, value);
    }

    private static void setStaticString(Class<?> cls, String name, String value) {
        Field f;
        try {
            f = cls.getDeclaredField(name);
            f.setAccessible(true);
        } catch (Throwable t) {
            return;
        }
        // 1) Android (ART) par accessible static final field seedha set ho jata hai
        try {
            f.set(null, value);
            if (value.equals(f.get(null))) return;
        } catch (Throwable ignored) {
        }
        // 2) Fallback: Unsafe
        try {
            Object unsafe = getUnsafe();
            if (unsafe != null) {
                Class<?> uc = unsafe.getClass();
                Method base = uc.getMethod("staticFieldBase", Field.class);
                Method off = uc.getMethod("staticFieldOffset", Field.class);
                Method put = uc.getMethod("putObject", Object.class, long.class, Object.class);
                Object b = base.invoke(unsafe, f);
                long o = (Long) off.invoke(unsafe, f);
                put.invoke(unsafe, b, o, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object getUnsafe() {
        try {
            Class<?> uc = Class.forName("sun.misc.Unsafe");
            Field f = uc.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
