package br.scanner.pdf;

import android.app.Application;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;

import java.io.File;

/** Prepara a biblioteca de PDF e limpa arquivos temporários antigos. */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PDFBoxResourceLoader.init(getApplicationContext());
        Store.IO.execute(() -> {
            File[] old = new File(getCacheDir(), "work").listFiles();
            long limit = System.currentTimeMillis() - 24L * 3600 * 1000;
            if (old != null) for (File f : old) if (f.lastModified() < limit) Store.deleteTree(f);
        });
    }
}
