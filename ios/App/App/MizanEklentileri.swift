// Mizan'a özel iPhone eklentileri ve köprü ekranı.
// - MizanViewController: CRM'i açan ekran; uygulamaya özel eklentileri burada kaydeder.
// - MizanSes: sesli komut. iPhone'un kendi konuşma tanıması (Apple) ile söyleneni yazıya çevirir;
//   web sayfasına yalnızca metin döner, ses kaydı saklanmaz ve sunucuya gönderilmez.
//   Android'deki MizanSes ile aynı arayüz: dinle({ dil, baslik }) -> { metin, secenekler }, durdur().
import UIKit
import Capacitor
import Speech
import AVFoundation

class MizanViewController: CAPBridgeViewController {
    override open func capacitorDidLoad() {
        bridge?.registerPluginInstance(MizanSesPlugin())
    }
}

@objc(MizanSesPlugin)
public class MizanSesPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "MizanSesPlugin"
    public let jsName = "MizanSes"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "dinle", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "durdur", returnType: CAPPluginReturnPromise),
    ]

    private let motor = AVAudioEngine()
    private var istek: SFSpeechAudioBufferRecognitionRequest?
    private var gorev: SFSpeechRecognitionTask?
    private var bekleyen: CAPPluginCall?
    private var sessizlik: Timer?
    private var sinir: Timer?
    private var sonMetin = ""
    private var secenekler: [String] = []

    @objc func dinle(_ call: CAPPluginCall) {
        let dil = call.getString("dil") ?? "tr-TR"
        DispatchQueue.main.async { self.bitir() } // önceki dinleme açık kaldıysa kapat
        SFSpeechRecognizer.requestAuthorization { durum in
            DispatchQueue.main.async {
                guard durum == .authorized else {
                    call.reject("Konuşma tanıma izni verilmedi. Ayarlar > Mizan bölümünden izin verebilirsiniz.", "IZIN")
                    return
                }
                AVAudioSession.sharedInstance().requestRecordPermission { izin in
                    DispatchQueue.main.async {
                        guard izin else {
                            call.reject("Mikrofon izni verilmedi. Ayarlar > Mizan bölümünden izin verebilirsiniz.", "IZIN")
                            return
                        }
                        self.baslat(call, dil: dil)
                    }
                }
            }
        }
    }

    @objc func durdur(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.istek?.endAudio() // söylenenin son hâli gelince bitir() sonucu döndürür
            self.sessizlikKur(0.8)
            call.resolve()
        }
    }

    private func baslat(_ call: CAPPluginCall, dil: String) {
        guard let tanici = SFSpeechRecognizer(locale: Locale(identifier: dil)), tanici.isAvailable else {
            call.reject("Ses tanıma şu an kullanılamıyor. İnternet bağlantınızı kontrol edin ya da yazarak devam edin.", "YOK")
            return
        }
        let yeni = SFSpeechAudioBufferRecognitionRequest()
        yeni.shouldReportPartialResults = true
        do {
            let oturum = AVAudioSession.sharedInstance()
            try oturum.setCategory(.record, mode: .measurement, options: .duckOthers)
            try oturum.setActive(true, options: .notifyOthersOnDeactivation)
            let giris = motor.inputNode
            giris.removeTap(onBus: 0)
            giris.installTap(onBus: 0, bufferSize: 1024, format: giris.outputFormat(forBus: 0)) { tampon, _ in
                yeni.append(tampon)
            }
            motor.prepare()
            try motor.start()
        } catch {
            temizle()
            call.reject("Mikrofon başlatılamadı. Başka bir uygulama mikrofonu kullanıyor olabilir.", "MIKROFON")
            return
        }
        istek = yeni
        bekleyen = call
        sonMetin = ""
        secenekler = []
        sinir = Timer.scheduledTimer(withTimeInterval: 20, repeats: false) { [weak self] _ in self?.bitir() }
        sessizlikKur(5) // hiç konuşulmazsa 5 saniye sonra kapanır
        gorev = tanici.recognitionTask(with: yeni) { [weak self] sonuc, hata in
            DispatchQueue.main.async {
                guard let self = self, self.bekleyen != nil else { return }
                if let s = sonuc {
                    self.sonMetin = s.bestTranscription.formattedString
                    self.secenekler = s.transcriptions.prefix(5).map { $0.formattedString }
                    if s.isFinal { self.bitir(); return }
                    self.sessizlikKur(1.6) // konuşma bitince kısa bir sessizlikten sonra kapanır
                }
                if hata != nil { self.bitir() }
            }
        }
    }

    private func sessizlikKur(_ sure: TimeInterval) {
        sessizlik?.invalidate()
        sessizlik = Timer.scheduledTimer(withTimeInterval: sure, repeats: false) { [weak self] _ in self?.bitir() }
    }

    private func bitir() {
        let call = bekleyen
        bekleyen = nil
        let metin = sonMetin
        let liste = secenekler
        temizle()
        call?.resolve(["metin": metin, "secenekler": liste])
    }

    private func temizle() {
        sessizlik?.invalidate(); sessizlik = nil
        sinir?.invalidate(); sinir = nil
        if motor.isRunning { motor.stop() }
        motor.inputNode.removeTap(onBus: 0)
        istek?.endAudio()
        gorev?.cancel()
        istek = nil
        gorev = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}
