package com.family.connect

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.family.connect.databinding.ActivityPairingBinding

/**
 * Màn hình đầu tiên khi cài app lần đầu (chưa ghép nối): nhập Mã gia đình +
 * tên bé -> gửi YÊU CẦU ghép nối (chưa có quyền gì cả) -> chờ phụ huynh bấm
 * Duyệt trên dashboard -> mới thực sự vào được app.
 */
class PairingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPairingBinding
    private var pendingFamilyCode: String? = null
    private var pendingDeviceUid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.connectButton.setOnClickListener {
            submitPairingRequest()
        }
    }

    private fun submitPairingRequest() {
        val familyCode = binding.familyCodeInput.text.toString().trim().uppercase()
        val childName = binding.childNameInput.text.toString().trim()
        val childGrade = binding.childGradeInput.text.toString().trim()

        if (familyCode.isEmpty() || childName.isEmpty()) {
            showError("Vui lòng nhập đủ Mã gia đình và Tên bé")
            return
        }

        showError(null)
        setFormEnabled(false)
        binding.connectButton.text = "Đang gửi yêu cầu..."

        AuthHelper.signInAnonymously { uid ->
            if (uid == null) {
                showError("Không kết nối được, kiểm tra mạng và thử lại.")
                setFormEnabled(true)
                binding.connectButton.text = "Kết nối"
                return@signInAnonymously
            }
            pendingFamilyCode = familyCode
            pendingDeviceUid = uid

            FirebaseSyncManager.requestPairing(familyCode, uid, childName, childGrade) { success ->
                runOnUiThread {
                    if (success) {
                        showWaitingState()
                        listenForApproval(familyCode, uid)
                    } else {
                        showError("Không gửi được yêu cầu, kiểm tra lại Mã gia đình và mạng.")
                        setFormEnabled(true)
                        binding.connectButton.text = "Kết nối"
                    }
                }
            }
        }
    }

    private fun listenForApproval(familyCode: String, deviceUid: String) {
        FirebaseSyncManager.listenForPairingApproval(familyCode, deviceUid) { childId ->
            runOnUiThread {
                PairingPrefs.savePairing(this, familyCode, childId, deviceUid)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
        }
    }

    private fun showWaitingState() {
        binding.waitingText.visibility = View.VISIBLE
        binding.waitingText.text =
            "Đã gửi yêu cầu ghép nối!\n\nNhờ ba mẹ mở dashboard và bấm \"Duyệt\" cho thiết bị này. Màn hình sẽ tự chuyển tiếp ngay khi được duyệt — cứ để yên app ở đây."
    }

    private fun setFormEnabled(enabled: Boolean) {
        binding.familyCodeInput.isEnabled = enabled
        binding.childNameInput.isEnabled = enabled
        binding.childGradeInput.isEnabled = enabled
        binding.connectButton.isEnabled = enabled
    }

    private fun showError(message: String?) {
        binding.errorText.text = message ?: ""
        binding.errorText.visibility = if (message != null) View.VISIBLE else View.GONE
    }
}
