package com.mediminder.app.data.model

enum class MedicineForm(val defaultUnit: DoseUnit) {
    TABLET(DoseUnit.TABLET),
    CAPSULE(DoseUnit.CAPSULE),
    SYRUP(DoseUnit.ML),
    INJECTION(DoseUnit.ML),
    DROPS(DoseUnit.DROP),
    INHALER(DoseUnit.PUFF),
    CREAM(DoseUnit.APPLICATION),
    SPRAY(DoseUnit.SPRAY),
    PATCH(DoseUnit.PATCH),
    POWDER(DoseUnit.SACHET),
    SUPPOSITORY(DoseUnit.PIECE),
    OTHER(DoseUnit.UNIT),
}

enum class DoseUnit {
    TABLET, CAPSULE, ML, TEASPOON, TABLESPOON, DROP, PUFF, SPRAY,
    UNIT, MG, G, MCG, PATCH, SACHET, APPLICATION, PIECE,
}

enum class MealInstruction { NONE, BEFORE_MEAL, AFTER_MEAL, WITH_MEAL, EMPTY_STOMACH, BEDTIME }

enum class ScheduleType { DAILY, SPECIFIC_DAYS, EVERY_X_DAYS, EVERY_X_HOURS, AS_NEEDED }

enum class AlarmStyle { NOTIFICATION, ALARM }

enum class DoseStatus { TAKEN, SKIPPED }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Bit mask with all seven days set. Bit 0 = Monday … bit 6 = Sunday. */
const val ALL_DAYS = 0b1111111
