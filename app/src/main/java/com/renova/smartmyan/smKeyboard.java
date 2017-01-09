package com.renova.smartmyan;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.Keyboard;
import android.view.inputmethod.EditorInfo;

public class smKeyboard extends Keyboard {

    private Key mEnterKey;

    //to store thd current state of the space key
    private Key mSpaceKey;

    //to store the current state of the language switch key
    private Key mLanguageSwitchKey;

    //to store the size and place of the space key
    private Key mSavedSpaceKey;

    //to store the size(visible or not) of the language switch key;
    private Key mSavedLanguageSwitchKey;

    public smKeyboard(Context context, int xmlLayoutResId) {
        super(context, xmlLayoutResId);
    }

    @Override
    protected Key createKeyFromXml(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
        Key key = new smKey(res, parent, x, y, parser);
        if (key.codes[0] == 10) {
            mEnterKey = key;
        } else if (key.codes[0] == ' ') {
            mSpaceKey = key;
            mSavedSpaceKey = new smKey(res, parent, x, y, parser);
        } else if (key.codes[0] == smKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            mLanguageSwitchKey = key;
            mSavedLanguageSwitchKey = new smKey(res, parent, x, y, parser);
        }
        return key;
    }

    //to change the visibility of the language switch key, key is visible if true
    void setLanguageSwitchKeyVisibility(boolean visible) {
        if (visible) {
            mSpaceKey.width = mSavedSpaceKey.width;
            mSpaceKey.x = mSavedSpaceKey.x;
            mLanguageSwitchKey.width = mSavedLanguageSwitchKey.width;
            mLanguageSwitchKey.icon = mSavedLanguageSwitchKey.icon;
            mLanguageSwitchKey.iconPreview = mSavedLanguageSwitchKey.iconPreview;
        } else {
            mSpaceKey.width = mSavedSpaceKey.width + mSavedLanguageSwitchKey.width;
            mSpaceKey.x = mSavedLanguageSwitchKey.x;
            mLanguageSwitchKey.width = 0;
            mLanguageSwitchKey.icon = null;
            mLanguageSwitchKey.iconPreview = null;
        }
    }

    //checking the ime options given by the current editor, to set the appropriate label on keyboard's enter key
    void setImeOptions(Resources res, int options) {
        if (mEnterKey == null) {
            return;
        }

        switch (options&(EditorInfo.IME_MASK_ACTION|EditorInfo.IME_FLAG_NO_ENTER_ACTION)) {
            case EditorInfo.IME_ACTION_GO:
                mEnterKey.iconPreview = null;
                mEnterKey.icon = null;
                mEnterKey.label = res.getText(R.string.label_go_key);
                break;
            case EditorInfo.IME_ACTION_NEXT:
                mEnterKey.iconPreview = null;
                mEnterKey.icon = null;
                mEnterKey.label = res.getText(R.string.label_next_key);
                break;
            case EditorInfo.IME_ACTION_SEARCH:
                mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_search);
                mEnterKey.label = null;
                break;
            case EditorInfo.IME_ACTION_SEND:
                mEnterKey.iconPreview = null;
                mEnterKey.icon = null;
                mEnterKey.label = res.getText(R.string.label_send_key);
                break;
            default:
                mEnterKey.icon = res.getDrawable(R.drawable.sym_keyboard_return);
                mEnterKey.label = null;
                break;
        }
    }

    static class smKey extends Keyboard.Key {

        public smKey(Resources res, Keyboard.Row parent, int x, int y, XmlResourceParser parser) {
            super(res, parent, x, y, parser);
        }
    }

    public void setSubtypeOnSpaceKey(String subTypeName, Drawable icon) {
        mSpaceKey.label=subTypeName;
        mSpaceKey.icon=icon;
    }
}