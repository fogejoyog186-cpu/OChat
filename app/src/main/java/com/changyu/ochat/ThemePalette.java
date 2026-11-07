package com.changyu.ochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;

/** Resolves the app palette from explicit user colors, theme mode, and device night mode. */
public final class ThemePalette {
    public final int ink, muted, primary, page, surface, input, onPrimary; public final boolean dark;
    private ThemePalette(int ink, int muted, int primary, int page, int surface, int input, int onPrimary, boolean dark) {
        this.ink=ink; this.muted=muted; this.primary=primary; this.page=page; this.surface=surface; this.input=input; this.onPrimary=onPrimary; this.dark=dark;
    }
    public static ThemePalette resolve(Context context) {
        SharedPreferences p=context.getSharedPreferences("chat",Context.MODE_PRIVATE);
        if(!p.contains("themeMode")&&(p.contains("themePrimary")||p.contains("themeBackground")||p.contains("themeSurface")||p.contains("themeText"))){p.edit().remove("themePrimary").remove("themeBackground").remove("themeSurface").remove("themeText").putString("themeMode","system").apply();}
        String mode=p.getString("themeMode","system");
        boolean deviceDark=(context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        boolean dark="dark".equals(mode) || (!"light".equals(mode) && deviceDark);
        int primary=read(p,"themePrimary",dark?"#75D5E8":"#006D83");
        int page=read(p,"themeBackground",dark?"#131B20":"#F4F8FA");
        int surface=read(p,"themeSurface",dark?"#1D2931":"#FFFFFF");
        int ink=read(p,"themeText",dark?"#E1EBF0":"#243237");
        int muted=dark?Color.rgb(169,187,196):Color.rgb(96,113,123);
        // Transparent overrides cannot provide a stable contrast contract.
        page=opaque(page); surface=opaque(surface); primary=opaque(primary);
        ink=readable(ink,page,surface);
        primary=readable(primary,page,surface);
        muted=readable(muted,page,surface);
        int input=surface;
        int onPrimary=contrast(primary);
        return new ThemePalette(ink,muted,primary,page,surface,input,onPrimary,dark);
    }
    private static int read(SharedPreferences p,String key,String fallback){String value=p.getString(key,null);if(value==null)return Color.parseColor(fallback);try{return Color.parseColor(value);}catch(Exception e){return Color.parseColor(fallback);}}
    private static int opaque(int color){return color | 0xff000000;}
    /** Keep requested color where legible, otherwise choose the better WCAG foreground. */
    public static int readable(int requested,int... backgrounds){
        requested=opaque(requested);
        double requestedMin=21,blackMin=21,whiteMin=21;
        for(int background:backgrounds){
            background=opaque(background);
            requestedMin=Math.min(requestedMin,androidx.core.graphics.ColorUtils.calculateContrast(requested,background));
            blackMin=Math.min(blackMin,androidx.core.graphics.ColorUtils.calculateContrast(Color.BLACK,background));
            whiteMin=Math.min(whiteMin,androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE,background));
        }
        return requestedMin>=4.5?requested:blackMin>=whiteMin?Color.BLACK:Color.WHITE;
    }
    public static int contrast(int color){return readable(Color.WHITE,color);}
    public static void applyActivityTheme(Context context){context.setTheme(resolve(context).dark?R.style.AppTheme_Dark:R.style.AppTheme_Light);}
}
