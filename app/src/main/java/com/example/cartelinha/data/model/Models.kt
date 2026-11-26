package com.example.cartelinha.data.model

/** Perfil de utilização do app — não é uma limitação de gênero. */
enum class UserRole(val label: String) {
    WOMAN("Mulher"),
    MAN("Homem")
}

/** Como o método é utilizado. */
enum class SchemeType {
    /** Cartela de comprimidos com ciclo de pausa/placebo. */
    PACK,
    /** Uso contínuo, cartela após cartela sem pausa. */
    CONTINUOUS,
    /** Dose periódica (injetável, adesivo, anel): uma dose a cada N dias. */
    PERIODIC
}

/** Tipo do dia dentro do ciclo contraceptivo. */
enum class SlotType {
    ACTIVE_PILL,
    PLACEBO_PILL,
    PAUSE,
    INTERVAL,
    NONE
}

/** Status de um registro diário. */
enum class RecordStatus {
    TAKEN,
    MISSED,
    SKIPPED_PLACEBO
}

/** Situação atual da cartela no momento do cadastro. */
enum class StartSituation {
    NOT_STARTED,
    IN_PROGRESS,
    IN_PAUSE,
    AWAITING_NEW_PACK
}

/** Um método anticoncepcional do catálogo (ou cadastrado manualmente). */
data class ContraceptiveMethod(
    val id: String,
    val name: String,
    val description: String,
    val iconKey: String,
    val schemeType: SchemeType,
    val packSize: Int = 0,
    val activeCount: Int = 0,
    val pauseDays: Int = 0,
    val doseIntervalDays: Int = 0,
    val isPreset: Boolean = true
)

/** Configuração persistida do anticoncepcional da usuária. */
data class ContraceptiveConfig(
    val id: Int = 1,
    val methodId: String,
    val customName: String,
    val schemeType: SchemeType,
    val packSize: Int,
    val activeCount: Int,
    val pauseDays: Int,
    val doseIntervalDays: Int,
    /** Data âncora (epochDay) do início do ciclo/cartela atual. */
    val cycleAnchorEpochDay: Long,
    val reminderMinutesOfDay: Int,
    val remindersEnabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long
) {
    val displayName: String get() = customName.ifBlank { methodId }
    /** Placebos = comprimidos da cartela que não são ativos. */
    val placeboCount: Int get() = (packSize - activeCount).coerceAtLeast(0)
    /** Tamanho total do ciclo que se repete. */
    /**
     * Tamanho do ciclo que se repete.
     * No esquema periódico, [packSize] é o número de aplicações,
     * [doseIntervalDays] o espaço entre elas e [pauseDays] a pausa final.
     * Injetável: 1 aplicação, pausa 0 — o ciclo continua igual ao intervalo.
     */
    val cycleLength: Int
        get() = when (schemeType) {
            SchemeType.PACK -> packSize + pauseDays
            SchemeType.CONTINUOUS -> packSize.coerceAtLeast(1)
            SchemeType.PERIODIC -> {
                val doses = packSize.coerceAtLeast(1)
                val gap = doseIntervalDays.coerceAtLeast(1)
                doses * gap + pauseDays.coerceAtLeast(0)
            }
        }
}

/** Catálogo de métodos anticoncepcionais. */
object MethodCatalog {

    val pillPackMethods = listOf(
        ContraceptiveMethod(
            id = "pill_21", name = "Cartela 21 + pausa de 7",
            description = "21 comprimidos ativos e, em seguida, 7 dias sem comprimido.",
            iconKey = "pill", schemeType = SchemeType.PACK,
            packSize = 21, activeCount = 21, pauseDays = 7
        ),
        ContraceptiveMethod(
            id = "pill_24_break", name = "Cartela 24 + pausa de 4",
            description = "24 comprimidos ativos e, em seguida, 4 dias sem comprimido.",
            iconKey = "pill", schemeType = SchemeType.PACK,
            packSize = 24, activeCount = 24, pauseDays = 4
        ),
        ContraceptiveMethod(
            id = "pill_28", name = "Cartela 28 (21 + 7 placebos)",
            description = "28 comprimidos na mesma cartela: 21 ativos e 7 placebos. Toma-se todo dia.",
            iconKey = "pill", schemeType = SchemeType.PACK,
            packSize = 28, activeCount = 21, pauseDays = 0
        ),
        ContraceptiveMethod(
            id = "pill_24_4", name = "Cartela 28 (24 + 4 placebos)",
            description = "Esquema 24/4: 24 comprimidos ativos e 4 placebos na mesma cartela.",
            iconKey = "pill", schemeType = SchemeType.PACK,
            packSize = 28, activeCount = 24, pauseDays = 0
        ),
        ContraceptiveMethod(
            id = "pill_26_2", name = "Cartela 28 (26 + 2 placebos)",
            description = "28 comprimidos na mesma cartela: 26 ativos e 2 placebos.",
            iconKey = "pill", schemeType = SchemeType.PACK,
            packSize = 28, activeCount = 26, pauseDays = 0
        )
    )

    val otherMethods = listOf(
        ContraceptiveMethod(
            id = "minipill", name = "Minipílula",
            description = "Pílula só de progestagênio, todos os dias, sem pausa. A janela de atraso depende da substância — confira a bula.",
            iconKey = "pill", schemeType = SchemeType.CONTINUOUS,
            packSize = 28, activeCount = 28, pauseDays = 0
        ),
        ContraceptiveMethod(
            id = "continuous", name = "Uso contínuo",
            description = "Cartela combinada sem a semana de pausa: ao terminar, comece a próxima no dia seguinte.",
            iconKey = "pill", schemeType = SchemeType.CONTINUOUS,
            packSize = 21, activeCount = 21, pauseDays = 0
        ),
        ContraceptiveMethod(
            id = "injectable_monthly", name = "Injetável mensal",
            description = "Injeção combinada com intervalo habitual de 30 dias.",
            iconKey = "syringe", schemeType = SchemeType.PERIODIC,
            packSize = 1, activeCount = 1, doseIntervalDays = 30, pauseDays = 0
        ),
        ContraceptiveMethod(
            id = "injectable_quarterly", name = "Injetável trimestral",
            description = "Injeção de progestagênio a cada 13 semanas (91 dias). Se a bula disser 3 meses, ajuste o intervalo.",
            iconKey = "syringe", schemeType = SchemeType.PERIODIC,
            packSize = 1, activeCount = 1, doseIntervalDays = 91, pauseDays = 0
        ),
        ContraceptiveMethod(
            id = "patch", name = "Adesivo contraceptivo",
            description = "Um adesivo novo a cada 7 dias, por 3 semanas, e depois 7 dias sem adesivo.",
            iconKey = "patch", schemeType = SchemeType.PERIODIC,
            packSize = 3, activeCount = 3, doseIntervalDays = 7, pauseDays = 7
        ),
        ContraceptiveMethod(
            id = "ring", name = "Anel vaginal",
            description = "Fica no lugar por 21 dias e sai por 7. Se o seu uso é sem essa pausa, deixe os dias de pausa em 0.",
            iconKey = "ring", schemeType = SchemeType.PERIODIC,
            packSize = 1, activeCount = 1, doseIntervalDays = 21, pauseDays = 7
        ),
        ContraceptiveMethod(
            id = "custom", name = "Outro método",
            description = "Cadastre manualmente um esquema personalizado.",
            iconKey = "edit", schemeType = SchemeType.PACK,
            packSize = 21, activeCount = 21, pauseDays = 7, isPreset = false
        )
    )

    fun find(id: String): ContraceptiveMethod? =
        (pillPackMethods + otherMethods).firstOrNull { it.id == id }
}
