package com.renova.smartmyan;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.media.AudioManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

public class SmartMyan extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private InputMethodManager mInputMethodManager;

    private smKeyboardView mInputView;

    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;

    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private boolean consumeBksp;

    private smKeyboard enKeyboard;
    private smKeyboard enSymbolsKeyboard;
    private smKeyboard enSymbolsShiftedKeyboard;

    private smKeyboard myKeyboard;
    private smKeyboard myShiftedKeyboard;
    private smKeyboard mySymbolsKeyboard;
    private smKeyboard mySymbolsShiftedKeyboard;

    private smKeyboard mCurKeyboard;

    SharedPreferences sharedPref;

    private String mWordSeparators;

    private String tallACons;
    private String myanmarCons;
    private String myanmarMeds;
    private String myanmarVows;
    private String myanmarIdVows;
    private String myanmarNums;
    private String myanmarSyms;

    private String vowA;
    private String vowI;
    private String vowU;
    private String tone;
    private String asat;

    //main initialization of input method component.
    @Override
    public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);

        tallACons = getResources().getString(R.string.tall_a);
        myanmarCons = getResources().getString(R.string.myanmar_consonants);
        myanmarMeds = getResources().getString(R.string.myanmar_medials);
        myanmarVows = getResources().getString(R.string.myanmar_vow);
        myanmarIdVows = getResources().getString(R.string.id_vow);
        myanmarNums = getResources().getString(R.string.myanmar_num);
        myanmarSyms = getResources().getString(R.string.myanmar_sym);

        vowA = getResources().getString(R.string.vow_a);
        vowI = getResources().getString(R.string.vow_i);
        vowU = getResources().getString(R.string.vow_u);
        tone = getResources().getString(R.string.tone);
        asat = getResources().getString(R.string.asat);

    }

    //UI initialization. Called after creation and any configuration change
    @Override
    public void onInitializeInterface() {
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        if (enKeyboard != null) {
            //to re-build the keyboards if the available space has changed
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        enKeyboard = new smKeyboard(this, R.xml.qwerty_en);
        enSymbolsKeyboard = new smKeyboard(this, R.xml.symbols_en);
        enSymbolsShiftedKeyboard = new smKeyboard(this, R.xml.symbols_shift_en);

        myKeyboard = new smKeyboard(this, R.xml.qwerty_my);
        myShiftedKeyboard = new smKeyboard(this, R.xml.qwerty_shift_my);
        mySymbolsKeyboard = new smKeyboard(this, R.xml.symbols_my);
        mySymbolsShiftedKeyboard = new smKeyboard(this, R.xml.symbols_shift_my);

        mCurKeyboard = getKeyboard(getLocaleId());
    }

    @Override
    public View onCreateInputView() {

        //get settings
        MyConfig.setSoundOn(sharedPref.getBoolean("play_sound", true));
        MyConfig.setSwitchIme(sharedPref.getBoolean("switch_ime", false));

        mInputView = (smKeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        setSmKeyboard(mCurKeyboard);
        return mInputView;
    }

    @SuppressLint("NewApi")
    private void setSmKeyboard(smKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey = mInputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());
        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);
        mInputView.setKeyboard(nextKeyboard);
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        //get settings
        MyConfig.setSoundOn(sharedPref.getBoolean("play_sound", true));
        MyConfig.setSwitchIme(sharedPref.getBoolean("switch_ime", false));

        mComposing.setLength(0);

        if (!restarting) {
            // clear shift states.
            mMetaState = 0;
        }

        mPredictionOn = false;
        mCompletionOn = false;

        //initializing state base on the type of text being edited
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                //Numbers and dates default to the symbols keyboard
                mCurKeyboard = getSymbolsKeyboard(getLocaleId());
                break;
            case InputType.TYPE_CLASS_PHONE:
                //Phones will default to phone keyboard
                switch (getLocaleId()) {
                    case 1:
                        mCurKeyboard = enSymbolsKeyboard;
                        break;
                    case 2:
                        mCurKeyboard = mySymbolsShiftedKeyboard;
                        break;
                }
                break;
            case InputType.TYPE_CLASS_TEXT:
                // for general text editing, default to normal alphabetic keyboard and doing predictive text
                mCurKeyboard = getKeyboard(getLocaleId());
                mPredictionOn = true;

                //looking for variation of text to modify the behavior
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    //do not display prediction
                    mPredictionOn = false;
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    //prediction will not be useful for e-mail and URIs.
                    mPredictionOn = false;
                }

                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    //displaying prediction supplied by the editor if the input field is auto complete text view
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                //looking at the current state of the editor to decide whether alphabetic keyboard should start out shifted
                updateShiftKeyState(attribute);
                break;

            default:
                //for all other input types, default to alphabetic keyboard of appropriate subtype
                mCurKeyboard = getKeyboard(getLocaleId());
                updateShiftKeyState(attribute);
        }
        //updating the label on enter key depending on what the application says it will do
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    //resetting the state when editing in a field is done
    @Override
    public void onFinishInput() {
        super.onFinishInput();
        //clear current composing text and candidates
        mComposing.setLength(0);

        mCurKeyboard = getKeyboard(getLocaleId());

        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        //applying the selected keyboard to the input view
        setSmKeyboard(mCurKeyboard);
        mInputView.closing();
        final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
        mCurKeyboard.setSubtypeOnSpaceKey(getString(subtype.getNameResId()), getResources().getDrawable(R.drawable.sym_keyboard_space));
    }

    @Override
    protected void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
        if (mComposing.length() > 0) {
            commitTyped(getCurrentInputConnection());
        }
        super.onCurrentInputMethodSubtypeChanged(newSubtype);
        mCurKeyboard = getKeyboard(getLocaleId());
        mCurKeyboard.setSubtypeOnSpaceKey(getString(newSubtype.getNameResId()), getResources().getDrawable(R.drawable.sym_keyboard_space));
        setSmKeyboard(mCurKeyboard);
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private smKeyboard getKeyboard(int subTypeId) {
        switch (subTypeId) {
            case 1:
                return enKeyboard;
            case 2:
                return myKeyboard;
        }
        return mCurKeyboard;
    }

    private smKeyboard getSymbolsKeyboard(int subTypeId) {
        switch (subTypeId) {
            case 1:
                return enSymbolsKeyboard;
            case 2:
                return mySymbolsKeyboard;
        }
        return mCurKeyboard;
    }

    //dealing with the editor reporting movement of its cursor
    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }

    private void playClick(int keyCode) {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        switch (keyCode) {
            case 32:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_SPACEBAR);
                break;
            case Keyboard.KEYCODE_DONE:
            case 10:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_RETURN);
                break;
            case Keyboard.KEYCODE_DELETE:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
                break;
            default:
                am.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                //if there is a currently composing text, modifying that instead of letting the app delete itself
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                //letting the underlying text editor always handle these
                return false;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return super.onKeyUp(keyCode, event);
    }

    //helper function to commit any text being composed in to editor
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            int lastCharacter = mComposing.charAt(mComposing.length() - 1);
            if (lastCharacter == 9676 || lastCharacter == 4153) {
                mComposing.delete(mComposing.length() - 1, mComposing.length());
            }
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
        }
    }

    //helper to update shift state of the keyboard based on initial editor state
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && mInputView != null && enKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    //helper to determine if a given character code is alphabetic
    private boolean isAlphabet(int code) {
        if (enKeyboard == mInputView.getKeyboard() && Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    //helper to send a key down / key up pair to the current editor
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    //helper to send a character to the editor as raw key events
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    //implementation of KeyboardViewListener
    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            //handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
                handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
        } else if (primaryCode == smKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch();
        //} else if (primaryCode == smKeyboardView.KEYCODE_OPTIONS) {
            //add a menu or something later
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == enSymbolsKeyboard || current == enSymbolsShiftedKeyboard) {
                setSmKeyboard(enKeyboard);
            } else if (current == mySymbolsKeyboard || current == mySymbolsShiftedKeyboard) {
                setSmKeyboard(myKeyboard);
                myKeyboard.setShifted(false);
            } else if (current == enKeyboard) {
                setSmKeyboard(enSymbolsKeyboard);
                enSymbolsKeyboard.setShifted(false);
            } else {
                setSmKeyboard(mySymbolsKeyboard);
                mySymbolsKeyboard.setShifted(false);
            }
        } else if (primaryCode == 4140) {
            if (mComposing.length() > 0) {
                int previousChar = mComposing.charAt(mComposing.length() - 1);
                if (mComposing.length() > 1) {
                    int previousTwo = mComposing.charAt(mComposing.length() - 2);
                    if (mComposing.length() > 2) {
                        int previousThree = mComposing.charAt(mComposing.length() - 3);
                        if (previousChar == 'ေ' && isMyanmarCons(previousTwo) && previousThree == 4153) {
                            previousChar = mComposing.charAt(mComposing.length() - 4);
                            if (previousChar == '်') {
                                previousChar = mComposing.charAt(mComposing.length() - 1);
                            }
                        } else if (isMyanmarCons(previousChar) && previousTwo == 4153 && previousThree == 4154) {
                            previousChar = mComposing.charAt(mComposing.length() - 1);
                        } else if (previousTwo == 4153) {
                            previousChar = mComposing.charAt(mComposing.length() - 3);
                        }
                    }
                    if (previousChar == 'ေ') {
                        previousChar = previousTwo;
                    }
                }
                if (isTallA(previousChar)) {
                    primaryCode = 4139;
                    handleMyanmarText(primaryCode, keyCodes);
                } else {
                    handleMyanmarText(primaryCode, keyCodes);
                }
            } else {
                handleMyanmarText(primaryCode, keyCodes);
            }
        } else if ((primaryCode >= 4096 && primaryCode <= 4185) || primaryCode == 9676 || primaryCode == 57506 || primaryCode == 57705 || primaryCode == 61912 || primaryCode == 59496 || primaryCode == 62667 || primaryCode == 62720 || primaryCode == 59772 || primaryCode == 61240) {
            if (mInputView.getKeyboard() == myShiftedKeyboard) {
                handleMyanmarText(primaryCode, keyCodes);
                handleShift();
            } else {
                handleMyanmarText(primaryCode, keyCodes);
            }
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
        if (MyConfig.isSoundOn() && !consumeBksp) {
            playClick(primaryCode);
        }
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        final int subtypeId = getLocaleId();
        if (length > 1) {
            switch (subtypeId) {
                case 1:
                    mComposing.delete(length - 1, length);
                    getCurrentInputConnection().setComposingText(mComposing, 1);
                    break;
                case 2:
                    if (consumeBksp) {
                        consumeBksp = false;
                        break;
                    }
                    int one = mComposing.charAt(length - 1);
                    int two = mComposing.charAt(length - 2);
                    int three = 32;
                    int four = 32;
                    int five = 32;

                    if (length > 2) {
                        three = mComposing.charAt(length - 3);
                        four = 32;
                        five = 32;
                        if (length > 3) {
                            four = mComposing.charAt(length - 4);
                            five = 32;
                            if (length > 4) {
                                five = mComposing.charAt(length - 5);
                            }
                        }
                    }

                    if (three != 4153 && isMyanmarCons(two) && one == 4145) {
                        mComposing.delete(length - 2, length);
                        mComposing.append((char) 9676);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if ((isMyanmarMeds(two) && one == 4145) || (two == 4151 && one == 4154) || (two == 4143 && one == 4150)) {
                        mComposing.delete(length - 2, length - 1);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (isMyanmarCons(four) && three == 4153 && isMyanmarCons(two) && one == 4145) {
                        mComposing.delete(length - 4, length);
                        mComposing.append((char) 9676);
                        mComposing.append((char) four);
                        mComposing.append((char) 4153);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (three == 9676 && isMyanmarCons(two) && one == 4153) {
                        mComposing.delete(length - 3, length);
                        mComposing.append((char) two);
                        mComposing.append((char) 4145);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (four == 40 && isMyanmarCons(three) && two == 4154 && one == 41) {
                        mComposing.delete(length - 4, length);
                        mComposing.append((char) three);
                        mComposing.append((char) two);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (five == 40 && isMyanmarCons(four) && isMyanmarMeds(three) && two == 4154 && one == 41) {
                        mComposing.delete(length - 5, length);
                        mComposing.append((char) four);
                        mComposing.append((char) three);
                        mComposing.append((char) two);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (two == 4137 && one == 4145) {
                        mComposing.delete(length - 2, length);
                        mComposing.append((char) 4126);
                        mComposing.append((char) 4145);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (four == 4100 && three == 4154 && two == 4153 && isMyanmarCons(one)) {
                        mComposing.delete(length - 4, length - 1);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else if (five == 4100 && four == 4154 && three == 4153 && isMyanmarCons(two) && one == 4145) {
                        mComposing.delete(length - 5, length - 2);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    } else {
                        mComposing.delete(length - 1, length);
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                    }
                    break;
            }
        } else if (length > 0) {
            if (consumeBksp) {
                consumeBksp = false;
                return;
            }
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
        } else {
            switch (subtypeId) {
                case 1:
                    keyDownUp(KeyEvent.KEYCODE_DEL);
                    break;
                case 2:
                    if (consumeBksp) {
                        consumeBksp = false;
                        return;
                    }
                    int i = 1;
                    CharSequence getText = getCurrentInputConnection().getTextBeforeCursor(1, 0);
                    // null error fixed on issue of version 1.1
                    if ((getText == null) || (getText.length() <= 0)) {
                        return;// fixed on issue of version 1.2, cause=(getText is null)
                        // solution=(if getText is null, return)
                    }

                    Integer current;
                    int beforeLength = 0;
                    int currentLength = 1;
                    boolean asat = false;

                    current = Integer.valueOf(getText.charAt(0));
                    while (!(isMyanmarCons(current) || isMyanmarIdVow(current) || isMyanmarSyms(current) || current == 4174 || isMyanmarNums(current) || current == 4170 || current == 4171 || isWordSeparator(current))// or
                            // Word
                            // separator
                            && (beforeLength != currentLength)) {
                        i++;
                        if (isAsat(current)) {
                            getText = getCurrentInputConnection().getTextBeforeCursor(i, 0);
                            if (getText != null) {
                                if (isVowA(getText.charAt(0))) {
                                    asat = false;
                                } else {
                                    asat = true;
                                }
                            } else {
                                asat = false;
                            }
                        }
                        beforeLength = currentLength;
                        getText = getCurrentInputConnection().getTextBeforeCursor(i, 0);
                        currentLength = getText.length();
                        current = Integer.valueOf(getText.charAt(0));
                    }
                    if (beforeLength == currentLength) {
                        getCurrentInputConnection().deleteSurroundingText(1, 0);
                    } else {
                        int virama = 0;
                        getText = getCurrentInputConnection().getTextBeforeCursor(i + 1, 0);
                        if (getText != null)
                            virama = getText.charAt(0);
                        if (virama == 0x1039) {
                            getCurrentInputConnection().deleteSurroundingText(i, 0);
                            asat = false;
                            handleBackspace();
                        } else {
                            if (asat) {
                                getCurrentInputConnection().deleteSurroundingText(i, 0);
                                asat = false;
                                handleBackspace();
                            } else {
                                getCurrentInputConnection().deleteSurroundingText(i, 0);
                                asat = false;
                            }
                        }
                    }
            }
            updateShiftKeyState((getCurrentInputEditorInfo()));
        }
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }

        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (enKeyboard == currentKeyboard) {
            //alphabet keyboard
            checkToggleCapLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == enSymbolsKeyboard) {
            enSymbolsKeyboard.setShifted(true);
            setSmKeyboard(enSymbolsShiftedKeyboard);
            enSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == enSymbolsShiftedKeyboard) {
            enSymbolsShiftedKeyboard.setShifted(false);
            setSmKeyboard(enSymbolsKeyboard);
            enSymbolsKeyboard.setShifted(false);
        } else if (currentKeyboard == myKeyboard) {
            myKeyboard.setShifted(true);
            setSmKeyboard(myShiftedKeyboard);
            myShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == myShiftedKeyboard) {
            myShiftedKeyboard.setShifted(false);
            setSmKeyboard(myKeyboard);
            myKeyboard.setShifted(false);
        } else if (currentKeyboard == mySymbolsKeyboard) {
            mySymbolsKeyboard.setShifted(true);
            setSmKeyboard(mySymbolsShiftedKeyboard);
            mySymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mySymbolsShiftedKeyboard) {
            mySymbolsShiftedKeyboard.setShifted(false);
            setSmKeyboard(mySymbolsKeyboard);
            mySymbolsKeyboard.setShifted(false);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode)) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
        }
        else {
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
                getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
            } else {
                getCurrentInputConnection().commitText(String.valueOf((char) primaryCode), 1);
            }
        }
    }

    private void handleMyanmarText(int primaryCode, int[] keyCodes) {
        consumeBksp = false;
        int length = mComposing.length();
        if (length == 0) {
            CharSequence charBeforeCursor = getCurrentInputConnection().getTextBeforeCursor(2, 0);
            Integer charCodeBeforeCursor;
            Integer twoCharBeforeCursor;
            //if getTextBeforeCursor return null
            if (charBeforeCursor == null) {
                charBeforeCursor = "";
            }
            if (charBeforeCursor.length() == 1) {
                charCodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));
                twoCharBeforeCursor = 32;
            } else if (charBeforeCursor.length() > 1) {
                twoCharBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(0));
                charCodeBeforeCursor = Integer.valueOf(charBeforeCursor.charAt(1));
            } else {
                charCodeBeforeCursor = 32;
                twoCharBeforeCursor = 32;
            }
            //handling the output of some text already composed in the numpad
            if (!isMyanmarNums(charCodeBeforeCursor) && (primaryCode == 57705 || primaryCode == 61912 || primaryCode == 59496 || primaryCode == 62667 || primaryCode == 62720 || primaryCode == 59772 || primaryCode == 61240)) {
                switch (primaryCode) {
                    case 57705:
                        sendKey(4096);
                        sendKey(4155);
                        sendKey(4117);
                        sendKey(4154);
                        break;
                    case 61912:
                        sendKey(4123);
                        sendKey(4140);
                        break;
                    case 59496:
                        sendKey(4113);
                        sendKey(4145);
                        sendKey(4140);
                        sendKey(4100);
                        sendKey(4154);
                        break;
                    case 62667:
                        sendKey(4126);
                        sendKey(4145);
                        sendKey(4140);
                        sendKey(4100);
                        sendKey(4154);
                        sendKey(4152);
                        break;
                    case 62720:
                        sendKey(4126);
                        sendKey(4141);
                        sendKey(4116);
                        sendKey(4154);
                        sendKey(4152);
                        break;
                    case 59772:
                        sendKey(4116);
                        sendKey(4140);
                        sendKey(4123);
                        sendKey(4142);
                        break;
                    case 61240:
                        sendKey(4121);
                        sendKey(4141);
                        sendKey(4116);
                        sendKey(4101);
                        sendKey(4154);
                        break;
                }
            }
            //handling the combinations of text and numerals, and unit styles written in combination with numerals
            else if (isMyanmarNums(charCodeBeforeCursor) && (isMyanmarCons(primaryCode) || primaryCode == 9676 || isMyanmarIdVow(primaryCode) || primaryCode == 4174)) {
                mComposing.append((char) 32);
                mComposing.append((char) primaryCode);
            } else if (isMyanmarNums(charCodeBeforeCursor) && (primaryCode == 57705 || primaryCode == 61912 || primaryCode == 59496 || primaryCode == 62667 || primaryCode == 62720 || primaryCode == 59772 || primaryCode == 61240)) {
                switch (primaryCode) {
                    case 57705:
                        sendKey(32);
                        sendKey(4096);
                        sendKey(4155);
                        sendKey(4117);
                        sendKey(4154);
                        break;
                    case 61912:
                        sendKey(32);
                        sendKey(4123);
                        sendKey(4140);
                        break;
                    case 59496:
                        sendKey(32);
                        sendKey(4113);
                        sendKey(4145);
                        sendKey(4140);
                        sendKey(4100);
                        sendKey(4154);
                        break;
                    case 62667:
                        sendKey(32);
                        sendKey(4126);
                        sendKey(4145);
                        sendKey(4140);
                        sendKey(4100);
                        sendKey(4154);
                        sendKey(4152);
                        break;
                    case 62720:
                        sendKey(32);
                        sendKey(4126);
                        sendKey(4141);
                        sendKey(4116);
                        sendKey(4154);
                        sendKey(4152);
                        break;
                    case 59772:
                        sendKey(32);
                        sendKey(4116);
                        sendKey(4140);
                        sendKey(4123);
                        sendKey(4142);
                        break;
                    case 61240:
                        sendKey(32);
                        sendKey(4121);
                        sendKey(4141);
                        sendKey(4116);
                        sendKey(4101);
                        sendKey(4154);
                        break;
                }

            } else if (isMyanmarNums(charCodeBeforeCursor) && isMyanmarSyms(primaryCode)) {
                sendKey(32);
                sendKey(primaryCode);
            } else if (isMyanmarNums(charCodeBeforeCursor) && isVowA(primaryCode)) {
                sendKey(4139);
            } else if (isMyanmarNums(charCodeBeforeCursor) && (primaryCode == 4141 || primaryCode == 4146 || primaryCode == 4157)) {
                sendKey(primaryCode);
            } else if (isMyanmarNums(twoCharBeforeCursor) && (charCodeBeforeCursor == 4139 || charCodeBeforeCursor == 4157) && primaryCode == 4152) {
                sendKey(primaryCode);
            }
            //possible syllable initials
            else if (isMyanmarCons(primaryCode) || primaryCode == 9676 || isMyanmarIdVow(primaryCode) || primaryCode == 4174) {
                mComposing.append((char) primaryCode);
            }
            //handling myanmar symbols
            else if (twoCharBeforeCursor != 32 && isMyanmarSyms(primaryCode)) {
                sendKey(primaryCode);
            }
            //handling myanmar word separators
            else if (primaryCode == 4170 || primaryCode == 4171) {
                sendKey(primaryCode);
            }
            //handling myanmar numerals
            else if (isMyanmarNums(primaryCode)) {
                sendKey(primaryCode);
            }
            //preventing backspace if there was no output text
            else {
                consumeBksp = true;
            }
        }

        if (length > 0) {
            int one = mComposing.charAt(length - 1);

            //handling the output of some text already composed in the numpad
            if (length == 1 && (primaryCode == 57705 || primaryCode == 61912 || primaryCode == 59496 || primaryCode == 62667 || primaryCode == 62720 || primaryCode == 59772 || primaryCode == 61240)) {
                switch (primaryCode) {
                    case 57705:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4096);
                        sendKey(4155);
                        sendKey(4117);
                        sendKey(4154);
                        break;
                    case 61912:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4123);
                        sendKey(4140);
                        break;
                    case 59496:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4113);
                        sendKey(4145);
                        sendKey(4140);
                        sendKey(4100);
                        sendKey(4154);
                        break;
                    case 62667:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4126);
                        sendKey(4145);
                        sendKey(4140);
                        sendKey(4100);
                        sendKey(4154);
                        sendKey(4152);
                        break;
                    case 62720:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4126);
                        sendKey(4141);
                        sendKey(4116);
                        sendKey(4154);
                        sendKey(4152);
                        break;
                    case 59772:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4116);
                        sendKey(4140);
                        sendKey(4123);
                        sendKey(4142);
                        break;
                    case 61240:
                        commitTyped(getCurrentInputConnection());
                        sendKey(4121);
                        sendKey(4141);
                        sendKey(4116);
                        sendKey(4101);
                        sendKey(4154);
                        break;
                }
            }
            //short-cut for kinzi
            if (isMyanmarCons(one) && primaryCode == 57506) {
                mComposing.delete(length - 1, length);
                mComposing.append((char) 4100);
                mComposing.append((char) 4154);
                mComposing.append((char) 4153);
                mComposing.append((char) one);
            }
            //correcting visual order for vowel e
            else if (one == 9676 && isMyanmarCons(primaryCode)) {
                mComposing.delete(length - 1, length);
                mComposing.append((char) primaryCode);
                mComposing.append((char) 4145);
            }
            //consonants and vowel e behaviors
            else if (length == 1 && isMyanmarCons(primaryCode)) {
                mComposing.append((char) primaryCode);
            } else if (length == 1 && one != 9676 && (isMyanmarIdVow(primaryCode) || primaryCode == 4174)) {
                mComposing.append((char) primaryCode);
            } else if (length == 1 && one != 9676 && primaryCode == 9676) {
                mComposing.append((char) primaryCode);
            }

            //handling myanmar symbols and word separators
            if((one != 4153 && one != 9676) && (isMyanmarSyms(primaryCode) || primaryCode == 4170 || primaryCode == 4171)) {
                commitTyped(getCurrentInputConnection());
                sendKey(primaryCode);
                return;
            }
            //handling myanmar numerals
            else if (isMyanmarNums(primaryCode)) {
                commitTyped(getCurrentInputConnection());
                sendKey(32);
                sendKey(primaryCode);
                return;
            }
            //swapping vowel e and medial, and restricting illegal medial sequences
            if (isMyanmarCons(one) && isMyanmarMeds(primaryCode)) {
                mComposing.append((char) primaryCode);
            } else if (one == 4155 || one == 4156) {
                switch (primaryCode) {
                    case 4157:
                        mComposing.append((char) primaryCode);
                        break;
                    case 4158:
                        mComposing.append((char) primaryCode);
                        break;
                    case 4155:
                        consumeBksp = true;
                        break;
                    case 4156:
                        consumeBksp = true;
                        break;
                }
            } else if (one == 4157 && primaryCode == 4158) {
                mComposing.append((char) primaryCode);
            } else if (isMyanmarMeds(primaryCode)) {
                consumeBksp = true;
            }

            //vowel input restrictions
            if (length == 1 && primaryCode == 4154) {
                consumeBksp = true;
                return;
            } else if ((isMyanmarCons(one) || isMyanmarMeds(one) || isMyanmarVow(one)) && isMyanmarVow(primaryCode))  {
                mComposing.append((char) primaryCode);
            }

            //restricting illegal vowel sequences
            if ((isVowA(one) && isVowA(primaryCode)) || (isVowI(one) && isVowI(primaryCode)) ||
                    (isVowU(one) && isVowU(primaryCode)) || (one == 4146 && primaryCode == 4146) ||
                    (one == 4150 && primaryCode == 4150) || (isTone(one) && isTone(primaryCode)) ||
                    (one == 4154 && isAsat(primaryCode)) || (one == 4153 && isAsat(primaryCode)) ||
                    (isMyanmarCons(one) && isTone(primaryCode)) || (one == 4153 && isMyanmarVow(primaryCode)) ||
                    (one == 4154 && (primaryCode == 4150 || primaryCode == 4146 || isVowU(primaryCode) || isVowI(primaryCode) || isVowA(primaryCode))) ||
                    (isTone(one) && (isVowA(primaryCode) || isVowI(primaryCode) || isVowU(primaryCode) || primaryCode == 4150 || primaryCode == 4146 || isAsat(primaryCode))) ||
                    (one == 4150 && (primaryCode == 4146 || primaryCode == 4144 || isVowI(primaryCode) || isAsat(primaryCode) || isVowA(primaryCode))) ||
                    (one == 4146 && (primaryCode == 4152 || primaryCode == 4150 || isVowU(primaryCode) || isVowI(primaryCode) || isVowA(primaryCode) || isAsat(primaryCode))) ||
                    (one == 4145 && (primaryCode == 4150 || primaryCode == 4146 || isVowU(primaryCode) || isVowI(primaryCode) || primaryCode == 4154)) ||
                    (one == 4143 && (isAsat(primaryCode) || primaryCode == 4150 || primaryCode == 4146 || isVowI(primaryCode) || isVowA(primaryCode))) ||
                    (one == 4144 && (isAsat(primaryCode) || primaryCode == 4150 || primaryCode == 4146 || isVowI(primaryCode) || isVowA(primaryCode))) ||
                    (one == 4141 && (isAsat(primaryCode) || isTone(primaryCode) || primaryCode == 4146 || primaryCode == 4144 || isVowA(primaryCode))) ||
                    (one == 4142 && (isAsat(primaryCode) || primaryCode == 4151 || primaryCode == 4150 || primaryCode == 4146 || isVowU(primaryCode) || isVowI(primaryCode) || isVowA(primaryCode))) ||
                    (isVowA(one) && (primaryCode == 4153 || primaryCode == 4150 || primaryCode == 4146 || isVowU(primaryCode) || isVowI(primaryCode))) ||
                    (isMyanmarMeds(one) && isAsat(primaryCode))) {
                mComposing.delete(length, length + 1);
                consumeBksp = true;
            }
            //swapping some manuscript vowel sequences in accordance with unicode sequences
            else if ((one == 4150 && primaryCode == 4143) || (one == 4154 && primaryCode == 4151)) {
                mComposing.delete(length - 1, length +1);
                mComposing.append((char) primaryCode);
                mComposing.append((char) one);
            }

            //correcting some mistyped forms of independent vowels
            if (one == 4133 && primaryCode == 4142) {
                mComposing.delete(length - 1, length);
                mComposing.append((char) 4134);
            } else if (one == 4134 && primaryCode == 4152) {
                mComposing.append((char) primaryCode);
            } else if (one == 4133 && (primaryCode == 4144 || primaryCode == 4150)) {
                mComposing.append((char) primaryCode);
            } else if (one == 4126 && primaryCode == 4156) {
                mComposing.delete(length - 1, length + 1);
                mComposing.append((char) 4137);
            }

            if (length > 1) {
                int two = mComposing.charAt(length - 2);
                //handling the output of some text already composed in the numpad
                if (length == 2 && (primaryCode == 57705 || primaryCode == 61912 || primaryCode == 59496 || primaryCode == 62667 || primaryCode == 62720 || primaryCode == 59772 || primaryCode == 61240)) {
                    switch (primaryCode) {
                        case 57705:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4096);
                            sendKey(4155);
                            sendKey(4117);
                            sendKey(4154);
                            break;
                        case 61912:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4123);
                            sendKey(4140);
                            break;
                        case 59496:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4113);
                            sendKey(4145);
                            sendKey(4140);
                            sendKey(4100);
                            sendKey(4154);
                            break;
                        case 62667:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4126);
                            sendKey(4145);
                            sendKey(4140);
                            sendKey(4100);
                            sendKey(4154);
                            sendKey(4152);
                            break;
                        case 62720:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4126);
                            sendKey(4141);
                            sendKey(4116);
                            sendKey(4154);
                            sendKey(4152);
                            break;
                        case 59772:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4116);
                            sendKey(4140);
                            sendKey(4123);
                            sendKey(4142);
                            break;
                        case 61240:
                            commitTyped(getCurrentInputConnection());
                            sendKey(4121);
                            sendKey(4141);
                            sendKey(4116);
                            sendKey(4101);
                            sendKey(4154);
                            break;
                    }
                }
                //short-cut for kinzi
                if (isMyanmarCons(two) && (one == 4145 || isMyanmarMeds(one))  && primaryCode == 57506) {
                    mComposing.delete(length - 2, length);
                    mComposing.append((char) 4100);
                    mComposing.append((char) 4154);
                    mComposing.append((char) 4153);
                    mComposing.append((char) two);
                    mComposing.append((char) one);
                }

                //correcting visual order for vowel e
                if (isMyanmarCons(two) && one == 4145 && primaryCode == 4153) {
                    mComposing.delete(length - 2, length);
                    mComposing.append((char) 9676);
                    mComposing.append((char) two);
                    mComposing.append((char) primaryCode);
                    mComposing.delete(length - 2, length - 1);
                }

                //consonants and vowel e behaviors
                if (length == 2 && one != 9676 && isMyanmarCons(primaryCode)) {
                    mComposing.append((char) primaryCode);
                } else if (length == 2 && (one != 9676 && one != 4153) && (isMyanmarIdVow(primaryCode) || primaryCode == 4174)) {
                    mComposing.append((char) primaryCode);
                } else if (length == 2 && (one != 9676 && one != 4153) && primaryCode == 9676) {
                    mComposing.append((char) primaryCode);
                }

                //swapping vowel e and medial, and restricting illegal medial sequences
                if (isMyanmarCons(two) && one == 4154 && primaryCode == 4155) {
                    mComposing.append((char) primaryCode);
                } else if (two == 4154 && one == 4155 && isMyanmarMeds(primaryCode)) {
                    mComposing.delete(length, length + 1);
                    consumeBksp = true;
                } else if (isMyanmarCons(two) && one == 4145 && isMyanmarMeds(primaryCode)) {
                    mComposing.delete(length - 2, length);
                    mComposing.append((char) two);
                    mComposing.append((char) primaryCode);
                    mComposing.append((char) one);
                    consumeBksp = false;
                } else if ((two == 4155 || two == 4156) && one == 4145) {
                    switch (primaryCode) {
                        case 4157:
                            mComposing.delete(length - 1, length);
                            mComposing.append((char) primaryCode);
                            mComposing.append((char) one);
                            consumeBksp = false;
                            break;
                        case 4158:
                            mComposing.delete(length - 1, length);
                            mComposing.append((char) primaryCode);
                            mComposing.append((char) one);
                            consumeBksp = false;
                            break;
                    }
                } else if(two == 4157 && one == 4145 && primaryCode == 4158) {
                    mComposing.delete(length - 1, length);
                    mComposing.append((char) primaryCode);
                    mComposing.append((char) one);
                    consumeBksp = false;
                }

                //restricting illegal vowel sequences
                if ((isVowA(two) && one == 4154 && isTone(primaryCode)) || (isMyanmarCons(two) && one == 4150 && primaryCode == 4152) ||
                        (isMyanmarCons(two) && one == 4143 && isTone(primaryCode)) || (isMyanmarCons(two) && isVowA(one) && primaryCode == 4154) ||
                        (isMyanmarMeds(two) && isVowA(one) && primaryCode == 4154) || (two == 4145 && isVowA(one) && primaryCode == 4152) ||
                        ((two == 4150 || two == 4146 || two == 4144 || two == 4142) && isMyanmarCons(one) && primaryCode == 4154)) {
                    mComposing.delete(length, length + 1);
                    consumeBksp = true;
                }
                //swapping some manuscript vowel sequences in accordance with unicode sequences
                else if ((two == 4143 && one == 4150 && primaryCode == 4143) || (two == 4141 && one == 4150 && primaryCode == 4143) || (two == 4151 && one == 4154 && primaryCode == 4151)) {
                    mComposing.delete(length - 1, length);
                    consumeBksp = true;
                }

                //handling some final consonants and asat combinations for foreign transliterations
                if ((isTone(two) && isMyanmarCons(one) && primaryCode == 4154) ||
                        (two == 4154 && isMyanmarCons(one) && primaryCode == 4154)) {
                    mComposing.delete(length - 1, length + 1);
                    mComposing.append((char) 40);
                    mComposing.append((char) one);
                    mComposing.append((char) 4154);
                    mComposing.append((char) 41);
                }

                //correcting some mistyped forms of independent vowels
                if (two == 4126 && one == 4145 && primaryCode == 4156) {
                    mComposing.delete(length - 2, length + 1);
                    mComposing.append((char) 4137);
                    mComposing.append((char) 4145);
                }

                if (length > 2) {
                    int three = mComposing.charAt(length - 3);
                    //handling the output of some text already composed in the numpad
                    if (primaryCode == 57705 || primaryCode == 61912 || primaryCode == 59496 || primaryCode == 62667 || primaryCode == 62720 || primaryCode == 59772 || primaryCode == 61240) {
                        switch (primaryCode) {
                            case 57705:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4096);
                                sendKey(4155);
                                sendKey(4117);
                                sendKey(4154);
                                break;
                            case 61912:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4123);
                                sendKey(4140);
                                break;
                            case 59496:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4113);
                                sendKey(4145);
                                sendKey(4140);
                                sendKey(4100);
                                sendKey(4154);
                                break;
                            case 62667:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4126);
                                sendKey(4145);
                                sendKey(4140);
                                sendKey(4100);
                                sendKey(4154);
                                sendKey(4152);
                                break;
                            case 62720:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4126);
                                sendKey(4141);
                                sendKey(4116);
                                sendKey(4154);
                                sendKey(4152);
                                break;
                            case 59772:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4116);
                                sendKey(4140);
                                sendKey(4123);
                                sendKey(4142);
                                break;
                            case 61240:
                                commitTyped(getCurrentInputConnection());
                                sendKey(4121);
                                sendKey(4141);
                                sendKey(4116);
                                sendKey(4101);
                                sendKey(4154);
                                break;
                        }
                    }
                    //short-cut for kinzi
                    if (isMyanmarCons(three) && isMyanmarMeds(two) && one == 4145 && primaryCode == 57506) {
                        mComposing.delete(length - 3, length);
                        mComposing.append((char) 4100);
                        mComposing.append((char) 4154);
                        mComposing.append((char) 4153);
                        mComposing.append((char) three);
                        mComposing.append((char) two);
                        mComposing.append((char) one);
                    }

                    //correcting visual order for vowel e
                    if (three == 9676 && isMyanmarCons(two) && one == 4153 && isMyanmarCons(primaryCode)) {
                        mComposing.delete(length - 3, length);
                        mComposing.append((char) two);
                        mComposing.append((char) 4153);
                        mComposing.append((char) primaryCode);
                        mComposing.append((char) 4145);
                    }
                    //consonants and vowel e behaviors
                    else if (one != 9676 && isMyanmarCons(primaryCode)) {
                        mComposing.append((char) primaryCode);
                    } else if ((one != 9676 && one != 4153) && (isMyanmarIdVow(primaryCode) || primaryCode == 4174)) {
                        mComposing.append((char) primaryCode);
                    }
                    else if ((one != 9676 && one != 4153) && primaryCode == 9676) {
                        mComposing.append((char) primaryCode);
                    }

                    //handling some final consonants and asat combinations for foreign transliterations
                    if ((three == 4154 && isMyanmarCons(two) && (one == 4155 || one == 4158) && primaryCode == 4154) ||
                            (isTone(three) && isMyanmarCons(two) && (one == 4155 || one == 4158) && primaryCode == 4154)) {
                        mComposing.delete(length - 2, length + 1);
                        mComposing.append((char) 40);
                        mComposing.append((char) two);
                        mComposing.append((char) one);
                        mComposing.append((char) primaryCode);
                        mComposing.append((char) 41);
                    }

                    //correcting some mistyped forms of independent vowels
                    if (three == 4137 && two == 4145 && isVowA(one) && primaryCode == 4154) {
                        mComposing.delete(length - 3, length + 1);
                        mComposing.append((char) 4138);
                    }
                }
            }
        }
        getCurrentInputConnection().setComposingText(mComposing, 1);
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }

    @SuppressLint("NewApi")
    private void handleLanguageSwitch() {
        mInputMethodManager.switchToNextInputMethod(getToken(), !MyConfig.isSwitch());
    }

    private void checkToggleCapLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char) code));
    }

    private String getTallACons() {
        return tallACons;
    }

    public boolean isTallA(int code) {
        String tallACons = getTallACons();
        return tallACons.contains(String.valueOf((char) code));
    }

    private String getMyanmarMeds() {
        return myanmarMeds;
    }

    public boolean isMyanmarMeds(int code) {
        String Meds = getMyanmarMeds();
        return Meds.contains(String.valueOf((char) code));
    }

    private String getMyanmarCons() {
        return myanmarCons;
    }

    public boolean isMyanmarCons(int code) {
        String myanmarCons = getMyanmarCons();
        return myanmarCons.contains(String.valueOf((char) code));
    }

    private String getMyanmarVows() {
        return myanmarVows;
    }

    public boolean isMyanmarVow(int code) {
        String myanmarVows = getMyanmarVows();
        return myanmarVows.contains(String.valueOf((char) code));
    }

    private String getMyanmarIdVows() {
        return myanmarIdVows;
    }

    public boolean isMyanmarIdVow(int code) {
        String myanmarIdVows = getMyanmarIdVows();
        return myanmarIdVows.contains(String.valueOf((char) code));
    }

    private String getMyanmarNums() {
        return myanmarNums;
    }

    public boolean isMyanmarNums(int code) {
        String myanmarNums = getMyanmarNums();
        return myanmarNums.contains(String.valueOf((char) code));
    }

    private String getMyanmarSyms() {
        return myanmarSyms;
    }

    public boolean isMyanmarSyms(int code) {
        String myanmarSyms = getMyanmarSyms();
        return myanmarSyms.contains(String.valueOf((char) code));
    }

    private String getVowA() {
        return vowA;
    }

    public boolean isVowA(int code) {
        String vowA = getVowA();
        return vowA.contains(String.valueOf((char) code));
    }

    private String getVowI() {
        return vowI;
    }

    public boolean isVowI(int code) {
        String vowI = getVowI();
        return vowI.contains(String.valueOf((char) code));
    }

    private String getVowU() {
        return vowU;
    }

    public boolean isVowU(int code) {
        String vowU = getVowU();
        return vowU.contains(String.valueOf((char) code));
    }

    private String getTone() {
        return tone;
    }

    public boolean isTone(int code) {
        String tone = getTone();
        return tone.contains(String.valueOf((char) code));
    }

    private String getAsat() {
        return asat;
    }

    public boolean isAsat(int code) {
        String asat = getAsat();
        return asat.contains(String.valueOf((char) code));
    }

    private Integer getLocaleId() {
        int localeId = 1;
        try {
            localeId = Integer.valueOf((mInputMethodManager.getCurrentInputMethodSubtype().getExtraValue()));
        } catch (NumberFormatException ex) {
            localeId = 1;
        }
        return localeId;
    }

    public void swipeRight() {
    }

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }
}