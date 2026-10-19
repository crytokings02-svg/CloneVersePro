package com.newidentity.cloner.engine

import com.newidentity.cloner.model.DeviceInstance
import com.newidentity.cloner.model.DevicePreset
import kotlin.random.Random

/**
 * Multiple Virtual Devices - har clone ko alag device mil sakta hai.
 *
 * Total presets: 1000
 * Formula: 25 brands x 10 base devices x 4 regional/build variants = 1000
 */
object DeviceProfiles {

    // lazy is liye hai taa-ke neeche walay brands/variants pehle initialize ho jayen.
    val presets: List<DevicePreset> by lazy {
        buildPresets().also { list ->
            check(list.size == 1000) { "Device presets 1000 honay chahiye, milay ${list.size}" }
        }
    }

    fun randomPreset(): DevicePreset = presets.random()

    fun newInstance(preset: DevicePreset): DeviceInstance {
        val serial = randomSerial()
        val incremental = (100000 + Random.nextInt(899999)).toString()
        return preset.instantiate(serial, incremental)
    }

    fun randomInstance(): DeviceInstance = newInstance(randomPreset())

    private fun buildPresets(): List<DevicePreset> {
        val result = ArrayList<DevicePreset>(1000)

        for (brand in brands) {
            require(brand.devices.size == 10) {
                "${brand.displayName} ke base devices 10 honay chahiye, milay ${brand.devices.size}"
            }

            for (device in brand.devices) {
                for ((variantIndex, variant) in variants.withIndex()) {
                    val fullName = if (
                        device.name.startsWith(brand.displayName, ignoreCase = true)
                    ) {
                        device.name
                    } else {
                        "${brand.displayName} ${device.name}"
                    }

                    val hardware = brand.hardware[
                        (device.name.length + variantIndex) % brand.hardware.size
                    ]

                    result += DevicePreset(
                        label = "$fullName${variant.labelSuffix}",
                        brand = brand.brand,
                        manufacturer = brand.manufacturer,
                        model = device.model,
                        device = device.device,
                        product = device.device + variant.productSuffix,
                        hardware = hardware,
                        androidRelease = variant.androidRelease,
                        buildId = variant.buildId
                    )
                }
            }
        }

        return result
    }

    private fun randomSerial(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..11).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }

    private data class BaseDevice(
        val name: String,
        val model: String,
        val device: String
    )

    private data class BrandSpec(
        val displayName: String,
        val brand: String,
        val manufacturer: String,
        val hardware: List<String>,
        val devices: List<BaseDevice>
    )

    private data class Variant(
        val labelSuffix: String,
        val productSuffix: String,
        val androidRelease: String,
        val buildId: String
    )

    private val variants = listOf(
        Variant(" (Global)", "_global", "14", "UP1A.231005.007"),
        Variant(" (Europe)", "_eea", "14", "AP1A.240405.002"),
        Variant(" (India)", "_in", "13", "TP1A.220624.014"),
        Variant(" (China)", "_cn", "15", "AP3A.241005.015")
    )

    private fun devices(text: String): List<BaseDevice> {
        return text.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { row ->
                val parts = row.split('|').map { it.trim() }
                require(parts.size == 3) { "Ghalat device row: $row" }
                BaseDevice(
                    name = parts[0],
                    model = parts[1],
                    device = parts[2]
                )
            }
    }

    private val brands: List<BrandSpec> = listOf(
        BrandSpec(
            "Samsung", "samsung", "samsung", listOf("qcom", "exynos2400", "exynos1480"),
            devices(
                """Galaxy S24 Ultra|SM-S928B|e3q; Galaxy S24|SM-S921B|e1s;
                Galaxy S23 Ultra|SM-S918B|dm3q; Galaxy S23|SM-S911B|dm1q;
                Galaxy Z Fold6|SM-F956B|q6q; Galaxy Z Flip6|SM-F741B|b6q;
                Galaxy A55|SM-A556B|a55x; Galaxy A35|SM-A356B|a35x;
                Galaxy A15|SM-A155F|a15; Galaxy M55|SM-M556B|m55x"""
            )
        ),
        BrandSpec(
            "Google", "google", "Google", listOf("tensor", "husky", "lynx"),
            devices(
                """Pixel 9 Pro XL|Pixel 9 Pro XL|Komodo; Pixel 9 Pro|Pixel 9 Pro|caiman;
                Pixel 9|Pixel 9|tokay; Pixel 8 Pro|Pixel 8 Pro|husky;
                Pixel 8|Pixel 8|shiba; Pixel 8a|Pixel 8a|akita;
                Pixel 7 Pro|Pixel 7 Pro|cheetah; Pixel 7|Pixel 7|panther;
                Pixel 7a|Pixel 7a|lynx; Pixel Fold|Pixel Fold|Felix"""
            )
        ),
        BrandSpec(
            "Xiaomi", "Xiaomi", "Xiaomi", listOf("qcom", "mt6985"),
            devices(
                """Xiaomi 14 Ultra|24031PN0DC|Aurora; Xiaomi 14 Pro|23116PN5BC|shennong;
                Xiaomi 14|23127PN0CG|houji; Xiaomi 13 Pro|2210132G|Nuwa;
                Xiaomi 13|2211133G|fuxi; Xiaomi 12 Pro|2201122G|Zeus;
                Xiaomi 12|2201123G|Cupid; Xiaomi Mix Fold 4|24072PX77C|goku;
                Xiaomi Civi 4 Pro|24053PY09C|chenfeng; Xiaomi 14T Pro|2407FPN8EG|Rothko"""
            )
        ),
        BrandSpec(
            "Redmi", "Redmi", "Xiaomi", listOf("qcom", "mt6897", "mt6768"),
            devices(
                """Redmi K70 Pro|23117RK66C|manet; Redmi K70|2311DRK48C|Vermeer;
                Redmi Note 13 Pro+|23090RA98G|Zircon; Redmi Note 13 Pro|2312DRA50G|Garnet;
                Redmi Note 13|23129RA7EO|Sapphire; Redmi Note 12 Pro|22101316G|Ruby;
                Redmi Note 12|23021RAAEG|Topaz; Redmi 13C|23106RN0DA|Gale;
                Redmi 12|23053RN02A|Sky; Redmi A3|23129RN51X|Klein"""
            )
        ),
        BrandSpec(
            "POCO", "POCO", "Xiaomi", listOf("qcom", "mt6897", "mt6768"),
            devices(
                """POCO F6 Pro|23113RKC6G|Vermeer; POCO F6|24069PC21G|Peridot;
                POCO X6 Pro|2311DRK48G|Duchamp; POCO X6|23122PCD1G|Garnet;
                POCO M6 Pro|2312FPCA6G|Emerald; POCO M6|2404APC5FG|moon;
                POCO C65|2310FPCA4G|Gale; POCO F5 Pro|23049PCD8G|Mondrian;
                POCO F5|23049PCD8I|Marble; POCO X5 Pro|22101320I|Redwood"""
            )
        ),
        BrandSpec(
            "OnePlus", "OnePlus", "OnePlus", listOf("qcom", "mt6983"),
            devices(
                """OnePlus 12|CPH2573|OP595DL1; OnePlus 12R|CPH2585|OP5961L1;
                OnePlus Open|CPH2551|OP5973L1; OnePlus 11|CPH2449|OP594DL1;
                OnePlus Nord 4|CPH2663|OP5D2BL1; OnePlus Nord CE4|CPH2613|OP5CF9L1;
                OnePlus Nord 3|CPH2491|OP556FL1; OnePlus 10 Pro|NE2211|OP516FL1;
                OnePlus 10T|CPH2415|OP5552L1; OnePlus Ace 3|PJE110|OP5929L1"""
            )
        ),
        BrandSpec(
            "OPPO", "OPPO", "OPPO", listOf("qcom", "mt6985", "mt6877"),
            devices(
                """Find X7 Ultra|PHY110|OP5A37L1; Find X7|PHY106|OP5A35L1;
                Find N3|CPH2499|OP565FL1; Reno11 Pro|CPH2607|OP5A29L1;
                Reno11|CPH2599|OP5A27L1; Reno12 Pro|CPH2629|OP5C41L1;
                Reno12|CPH2625|OP5C39L1; A79|CPH2553|OP573BL1;
                A58|CPH2577|OP574FL1; A38|CPH2579|OP574GL1"""
            )
        ),
        BrandSpec(
            "vivo", "vivo", "vivo", listOf("qcom", "mt6985", "mt6886"),
            devices(
                """X100 Pro|V2309|V2309; X100|V2307|V2307;
                X90 Pro|V2219|V2219; V30 Pro|V2319|V2319;
                V30|V2318|V2318; V29|V2250|V2250;
                Y100|V2317|V2317; Y28|V2352|V2352;
                Y18|V2333|V2333; T3|V2334|V2334"""
            )
        ),
        BrandSpec(
            "iQOO", "iQOO", "vivo", listOf("qcom", "mt6985"),
            devices(
                """iQOO 12|I2220|I2220; iQOO Neo9 Pro|I2217|I2217;
                iQOO 11|I2201|I2201; iQOO Z9|I2302|I2302;
                iQOO Z7|I2207|I2207; iQOO Neo7|I2214|I2214;
                iQOO 9 Pro|I2022|I2022; iQOO 9|I2017|I2017;
                iQOO Z6|I2127|I2127; iQOO Z3|I2011|I2011"""
            )
        ),
        BrandSpec(
            "realme", "realme", "realme", listOf("qcom", "mt6877", "mt6835"),
            devices(
                """GT 6|RMX3851|RE5D3FL1; GT 6T|RMX3853|RE5D3GL1;
                12 Pro+|RMX3840|RE5C4EL1; 12 Pro|RMX3842|RE5C4FL1;
                12+|RMX3867|RE5C9FL1; 12|RMX3871|RE5C9GL1;
                11 Pro+|RMX3740|RE58C2L1; 11 Pro|RMX3771|RE58C4L1;
                Narzo 70 Pro|RMX3868|RE5CA6L1; C65|RMX3910|RE5D8FL1"""
            )
        ),
        BrandSpec(
            "Huawei", "HUAWEI", "HUAWEI", listOf("kirin9000s", "kirin9010"),
            devices(
                """Pura 70 Ultra|HBP-AL00|HBP; Pura 70 Pro|HBN-AL00|HBN;
                Pura 70|ADY-AL00|ADY; Mate 60 Pro|ALN-AL00|ALN;
                Mate 60|BRA-AL00|BRA; Mate X5|ALT-AL10|ALT;
                Nova 12 Pro|ADA-AL00|ADA; Nova 12|FIN-AL60|FIN;
                Nova 11|FOA-AL00|FOA; P60 Pro|MNA-AL00|MNA"""
            )
        ),
        BrandSpec(
            "Honor", "HONOR", "HONOR", listOf("qcom", "mt6877"),
            devices(
                """Magic6 Pro|BVL-N49|BVl; Magic6|BVL-AN00|BVL;
                Magic V2|VER-N49|VER; Honor 200 Pro|ELP-NX9|ELP;
                Honor 200|ELI-NX9|ELI; Honor 90|REA-NX9|REA;
                Honor X9b|ALI-NX1|ALI; Honor X8b|LLY-LX1|LLY;
                Magic7 Pro|PTP-N49|PTP; Honor 100|MAA-AN00|MAA"""
            )
        ),
        BrandSpec(
            "Motorola", "motorola", "motorola", listOf("qcom", "mt6879", "mt6833"),
            devices(
                """Edge 50 Ultra|XT2401-1|eqs; Edge 50 Pro|XT2403-2|cusco;
                Edge 50 Fusion|XT2429-2|malmo; Edge 40|XT2303-2|rtwo;
                Razr 50 Ultra|XT2451-3|arcfox; Razr 40 Ultra|XT2321-3|Zeus;
                Moto G84|XT2347-2|bangkk; Moto G54|XT2343-2|cancunf;
                Moto G34|XT2363-2|fogos; Moto G24|XT2423-2|fogo"""
            )
        ),
        BrandSpec(
            "Nokia", "Nokia", "HMD Global", listOf("qcom", "mt6833", "unisoc"),
            devices(
                """Nokia XR21|TA-1486|XR21; Nokia X30 5G|TA-1450|X30;
                Nokia G42|TA-1581|G42; Nokia G22|TA-1528|G22;
                Nokia C32|TA-1534|C32; Nokia C22|TA-1539|C22;
                HMD Pulse Pro|TA-1604|PulsePro; HMD Pulse+|TA-1602|PulsePlus;
                HMD Pulse|TA-1595|Pulse; HMD Skyline|TA-1688|Skyline"""
            )
        ),
        BrandSpec(
            "Sony", "Sony", "Sony", listOf("qcom"),
            devices(
                """Xperia 1 VI|XQ-EC72|XQ-EC72; Xperia 5 V|XQ-DE72|XQ-DE72;
                Xperia 10 VI|XQ-ES72|XQ-ES72; Xperia 1 V|XQ-DQ72|XQ-DQ72;
                Xperia 5 IV|XQ-CQ72|XQ-CQ72; Xperia 10 V|XQ-DC72|XQ-DC72;
                Xperia Pro-I|XQ-BE62|XQ-BE62; Xperia Ace III|SO-53C|SO-53C;
                Xperia 1 IV|XQ-CT72|XQ-CT72; Xperia 10 IV|XQ-CC72|XQ-CC72"""
            )
        ),
        BrandSpec(
            "ASUS", "asus", "asus", listOf("qcom"),
            devices(
                """ROG Phone 8 Pro|AI2401|AI2401; ROG Phone 8|AI2401_A|AI2401A;
                ROG Phone 7|AI2205|AI2205; ROG Phone 6|AI2201|AI2201;
                Zenfone 11 Ultra|AI2401_H|AI2401H; Zenfone 10|AI2302|AI2302;
                Zenfone 9|AI2202|AI2202; ROG Phone 5|ZS673KS|ZS673KS;
                Zenfone 8|ZS590KS|ZS590KS; Zenfone 8 Flip|ZS672KS|ZS672KS"""
            )
        ),
        BrandSpec(
            "Infinix", "Infinix", "Infinix", listOf("mt6789", "mt6833", "mt6768"),
            devices(
                """Note 40 Pro+|Infinix X6851B|Infinix-X6851B; Note 40 Pro|Infinix X6850|Infinix-X6850;
                Note 40|Infinix X6853|Infinix-X6853; Hot 40 Pro|Infinix X6837|Infinix-X6837;
                Hot 40i|Infinix X6528|Infinix-X6528; Zero 30|Infinix X6731|Infinix-X6731;
                GT 20 Pro|Infinix X6871|Infinix-X6871; Note 30|Infinix X6833B|Infinix-X6833B;
                Smart 8|Infinix X6525|Infinix-X6525; Hot 30|Infinix X6831|Infinix-X6831"""
            )
        ),
        BrandSpec(
            "TECNO", "TECNO", "TECNO", listOf("mt6789", "mt6833", "unisoc"),
            devices(
                """Camon 30 Premier|TECNO CL9|TECNO-CL9; Camon 30 Pro|TECNO CL8|TECNO-CL8;
                Camon 30|TECNO CL6|TECNO-CL6; Spark 20 Pro+|TECNO KJ7|TECNO-KJ7;
                Spark 20 Pro|TECNO KJ6|TECNO-KJ6; Spark 20|TECNO KJ5|TECNO-KJ5;
                Phantom V Fold|TECNO AD10|TECNO-AD10; Phantom X2|TECNO AD8|TECNO-AD8;
                Pova 6 Pro|TECNO LI9|TECNO-LI9; Pova 6|TECNO LI7|TECNO-LI7"""
            )
        ),
        BrandSpec(
            "itel", "itel", "itel", listOf("unisoc", "mt6761"),
            devices(
                """itel P65|itel P65|itel-P65; itel A70|A665L|itel-A665L;
                itel A60s|A662LM|itel-A662LM; itel S24|S685LN|itel-S685LN;
                itel P55|A666L|itel-A666L; itel P40|P662L|itel-P662L;
                itel A50|A667LP|itel-A667LP; itel RS4|S686LN|itel-S686LN;
                itel S23|S665L|itel-S665L; itel P55+|P663L|itel-P663L"""
            )
        ),
        BrandSpec(
            "TCL", "TCL", "TCL", listOf("qcom", "mt6833", "mt6765"),
            devices(
                """TCL 50 Pro|T803E|T803E; TCL 50 5G|T613K|T613K;
                TCL 40 XL|T608M|T608M; TCL 40 SE|T610K|T610K;
                TCL 30 5G|T776O|T776O; TCL 30 XE|T767W|T767W;
                TCL 20 Pro|T799B|T799B; TCL 20 SE|T671H|T671H;
                TCL 10 Pro|T799H|T799H; TCL 505|T509K|T509K"""
            )
        ),
        BrandSpec(
            "ZTE", "ZTE", "ZTE", listOf("qcom", "unisoc", "mt6833"),
            devices(
                """Axon 60 Ultra|A2025H|A2025H; Axon 50 Ultra|A2024H|A2024H;
                Axon 40 Ultra|A2023P|A2023P; Axon 30|A2322|A2322;
                Blade V50 Vita|A7050|A7050; Blade A73|A7040|A7040;
                Blade A54|A7030|A7030; Libero 5G III|A202ZT|A202ZT;
                Voyage 51|A4060|A4060; Changxing 60|A4030|A4030"""
            )
        ),
        BrandSpec(
            "nubia", "nubia", "nubia", listOf("qcom", "unisoc"),
            devices(
                """RedMagic 9S Pro|NX769S|NX769S; RedMagic 9 Pro|NX769J|NX769J;
                RedMagic 8 Pro|NX729J|NX729J; Z60 Ultra|NX721J|NX721J;
                Z50 Ultra|NX712J|NX712J; Flip 5G|NX724J|NX724J;
                Neo 2|Z2352N|Z2352N; Music|Z2353|Z2353;
                Focus Pro|Z2351N|Z2351N; V60|Z2350|Z2350"""
            )
        ),
        BrandSpec(
            "Nothing", "Nothing", "Nothing", listOf("qcom", "mt6886"),
            devices(
                """Phone (1)|A063|Spacewar; Phone (2)|A065|Pong;
                Phone (2a)|A142|Pacman; Phone (2a) Plus|A142P|PacmanPro;
                CMF Phone 1|A015|Tetris; Phone (1) 256GB|A063P|SpacewarP;
                Phone (2) 512GB|A065P|PongP; Phone (2a) 256GB|A142S|PacmanS;
                Phone (2a) Plus 256GB|A142PS|PacmanProS; CMF Phone 1 256GB|A015P|TetrisP"""
            )
        ),
        BrandSpec(
            "Lenovo", "Lenovo", "Lenovo", listOf("qcom", "mt6768"),
            devices(
                """Legion Y90|L71061|L71061; Legion Duel 2|L70081|L70081;
                Legion Phone Duel|L79031|L79031; Z6 Pro|L78051|L78051;
                Z5 Pro GT|L78032|L78032; S5 Pro GT|L58091|L58091;
                K10 Note|L38111|L38111; K10 Plus|L39051|L39051;
                K12|XT2081-4|XT2081; K13|XT2097-3|XT2097"""
            )
        ),
        BrandSpec(
            "LG", "lge", "LG Electronics", listOf("qcom", "mt6765"),
            devices(
                """Velvet|LM-G910|LM-G910; Wing|LM-F100|LM-F100;
                V60 ThinQ|LM-V600|LM-V600; G8X ThinQ|LM-G850|LM-G850;
                G8 ThinQ|LM-G820|LM-G820; V50 ThinQ|LM-V500|LM-V500;
                Q92|LM-Q920|LM-Q920; K92|LM-K920|LM-K920;
                Velvet 5G|LM-G900|LM-G900; Stylo 6|LM-Q730|LM-Q730"""
            )
        )
    )
}
