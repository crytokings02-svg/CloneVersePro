# CloneVerse Pro — App Cloner (New Identity)

**CloneVerse Pro** kisi installed app ki alag **Clone copy** banata hai.
Original app untouched rehti hai — sirf Cloned App ko nayi identity milti hai.

## Features

### Brand / Model
Choose another brand/model — the clone application will not get the real device brand/model.
12 Virtual Devices mojood hain (Samsung, Pixel, OnePlus, Xiaomi, Infinix, Tecno...).
Har clone ko alag device mil sakta hai (**Multiple Devices**), aur ek saath **1–5 clones** ban sakte hain.

### Open a new identity with one click
Ek click par saari IDs random:

- **Android ID** — Report the changed Android ID to the cloned application to prevent obtaining the native Android ID
- **IMEI/IMSI** — Change the IMEI/IMSI number of the phone in the clone application
- **WiFi/Bluetooth MAC address** — Change your WiFi/Bluetooth mac address
- **Google Advertising ID** — Report a random Google advertising ID to the cloned application to prevent it from being tracked by advertising
- **SIM card and carrier information** — Sirf ek switch ON: *Hide local SIM card serial number, phone number, phone location, carrier identifier/name/country and other information* (koi carrier select nahi karna)

## Kaise kaam karta hai

**Naya (v2.1):** Main page par kisi app par tap karo → *CLONE + INSTALL* → clone banta hai aur
seedha install hota hai (Android ka INSTALL prompt aye to dabao). Lamba dabao = Advanced Settings.
Split APK (App Bundle) apps ab poori clone hoti hain, aur install error ka asal message dikhta hai.

1. Phone ki apps ki list se app select karein (Search + System apps switch)
2. Virtual Device chunein + **OPEN A NEW IDENTITY WITH ONE CLICK**
3. **CLONE KARO** — engine: APK copy → package change → identity inject →
   SIM/Ad permissions hide → zip-align → nayi key se sign
4. Clone install karein — install ke baad app khud **RUN CLONE** ka option degi
5. **My Clones** mein saare clones, unki devices aur identity values milengi

## GitHub se APK banana (3–6 minute)

1. github.com par nayi **Public** repository banayein, naam masalan `CloneVersePro`
2. Is ZIP ko extract karke andar wale `CloneVersePro` folder ki **saari files** upload karein
   (`.github` hidden folder lazmi hai — Windows mein View → Hidden items ON karein)
3. **Actions** tab kholein → **Build APK** run green hone ka intzaar karein
4. Run par click → neeche **Artifacts → AppCloner-NewIdentity-APK** download karein
5. Us ZIP ke andar APK milegi — phone mein install karein (Unknown apps allow karein)

## Hadein (har cloner ki tarah)

- Split APKs, banking, Google-Login aur signature-check wali apps kabhi kabhi clone run nahi karti.
- Yeh version **standalone cloned APK** banata hai (App-Cloner style) — har clone alag install hoti hai,
  Parallel-Space jaisi andar virtual machine nahi hai.
- Sirf apne device par multi-account / privacy / testing ke liye istemal karein.
  Kisi service ki Terms torney ya fraud ke liye nahi.
