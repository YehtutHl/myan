package com.renova.smartmyan;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.Keyboard.Key;
import android.inputmethodservice.KeyboardView;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodSubtype;

import java.util.List;

public class smKeyboardView extends KeyboardView {

    static final int KEYCODE_OPTIONS = -100;
    static final int KEYCODE_LANGUAGE_SWITCH = -101;

    public smKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public smKeyboardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected boolean onLongPress(Key key) {
        if (key.codes[0] == Keyboard.KEYCODE_CANCEL) {
            getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
            return true;
        } else if (key.codes[0] == 'ာ') {
            getOnKeyboardActionListener().onKey(4139, null);
            return true;
        } else {
            return super.onLongPress(key);
        }
    }

    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<Key> keys = getKeyboard().getKeys();
        Paint paint = new Paint();
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize((float) getResources().getDimension(R.dimen.double_tap_key_size));
        paint.setAntiAlias(true);
        paint.setColor(getResources().getColor(R.color.text_color));
        for (Key key : keys) {
            if (key.label != null) {
                if (key.popupCharacters != null) {
                    String popKeyLabel = "";
                    int xPos = key.x + (key.width / 4) * 3;
                    int yPos = key.y + key.height / 3;
                    popKeyLabel = key.popupCharacters.toString();
                    canvas.drawText(popKeyLabel, xPos, yPos, paint);
                }
            }
        }
    }
}