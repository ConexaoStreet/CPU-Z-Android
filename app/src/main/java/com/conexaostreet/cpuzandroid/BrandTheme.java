package com.conexaostreet.cpuzandroid;

import android.content.Context;
import android.graphics.Color;

/**
 * Identidade visual CPU-Z Android: preto/vermelho e branco/vermelho.
 * Mantém as cores de função (avisos críticos) independentes da decoração.
 */
final class BrandTheme {
    static final int RED=Color.rgb(230,0,0);
    static final int RED_DARK=Color.rgb(139,0,0);
    static final int BLACK=Color.BLACK;
    static final int WHITE=Color.WHITE;
    private BrandTheme(){}
    static boolean isLight(Context c){
        return c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE)
            .getBoolean("light_theme",false);
    }
    static void setLight(Context c,boolean light){
        c.getSharedPreferences("diagnostics",Context.MODE_PRIVATE)
            .edit().putBoolean("light_theme",light).apply();
    }
    static int background(boolean light){return light?WHITE:BLACK;}
    static int surface(boolean light){return light?Color.rgb(246,246,246):Color.rgb(25,25,25);}
    static int border(boolean light){return light?Color.rgb(223,223,223):Color.rgb(60,60,60);}
    static int text(boolean light){return light?Color.rgb(20,20,20):WHITE;}
    static int muted(boolean light){return light?Color.rgb(96,96,96):Color.rgb(176,176,176);}
}
