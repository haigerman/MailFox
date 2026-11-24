package app.k9mail.provider

import androidx.compose.ui.graphics.Color
import net.thunderbird.components.ui.bolt.theme.ThemeColorScheme

// MailFox color theme "Herbstfuchs" (autumn fox) for Compose screens.
// Keep in sync with app-k9mail/src/main/res/values/mailfox_theme_colors.xml

internal val mailFoxLightColorScheme = ThemeColorScheme(
    primary = Color(color = 0xFF9B4000),
    onPrimary = Color(color = 0xFFFFFFFF),
    primaryContainer = Color(color = 0xFFC05408),
    onPrimaryContainer = Color(color = 0xFFFFFBFF),

    secondary = Color(color = 0xFF4C662B),
    onSecondary = Color(color = 0xFFFFFFFF),
    secondaryContainer = Color(color = 0xFFCBEAA0),
    onSecondaryContainer = Color(color = 0xFF506B2F),

    tertiary = Color(color = 0xFF775600),
    onTertiary = Color(color = 0xFFFFFFFF),
    tertiaryContainer = Color(color = 0xFF966E00),
    onTertiaryContainer = Color(color = 0xFFFFFBFF),

    error = Color(color = 0xFFBA1A1A),
    onError = Color(color = 0xFFFFFFFF),
    errorContainer = Color(color = 0xFFFFDAD6),
    onErrorContainer = Color(color = 0xFF93000A),

    surfaceDim = Color(color = 0xFFE5D8CC),
    surface = Color(color = 0xFFFFF8F4),
    surfaceBright = Color(color = 0xFFFFF8F4),
    onSurface = Color(color = 0xFF211A13),
    onSurfaceVariant = Color(color = 0xFF544435),

    surfaceContainerLowest = Color(color = 0xFFFFFFFF),
    surfaceContainerLow = Color(color = 0xFFFFF1E5),
    surfaceContainer = Color(color = 0xFFF9ECDF),
    surfaceContainerHigh = Color(color = 0xFFF3E6DA),
    surfaceContainerHighest = Color(color = 0xFFEDE0D4),

    inverseSurface = Color(color = 0xFF362F27),
    inverseOnSurface = Color(color = 0xFFFCEEE2),
    inversePrimary = Color(color = 0xFFFFB691),

    outline = Color(color = 0xFF867462),
    outlineVariant = Color(color = 0xFFD9C3AF),

    scrim = Color.Black,

    info = Color(color = 0xFF004F9B),
    onInfo = Color(color = 0xFFFFFFFF),
    infoContainer = Color(color = 0xFFF0F8FF),
    onInfoContainer = Color(color = 0xFF004F9B),

    success = Color(color = 0xFF2E5A1C),
    onSuccess = Color(color = 0xFFFFFFFF),
    successContainer = Color(color = 0xFFE8F3DA),
    onSuccessContainer = Color(color = 0xFF2E5A1C),

    warning = Color(color = 0xFF713F12),
    onWarning = Color(color = 0xFFFEFAE8),
    warningContainer = Color(color = 0xFFFEFAE8),
    onWarningContainer = Color(color = 0xFF713F12),
)

internal val mailFoxDarkColorScheme = ThemeColorScheme(
    primary = Color(color = 0xFFFFB691),
    onPrimary = Color(color = 0xFF552000),
    primaryContainer = Color(color = 0xFFE56F28),
    onPrimaryContainer = Color(color = 0xFF3C1400),

    secondary = Color(color = 0xFFB2D189),
    onSecondary = Color(color = 0xFF203701),
    secondaryContainer = Color(color = 0xFF354E15),
    onSecondaryContainer = Color(color = 0xFFA1BF79),

    tertiary = Color(color = 0xFFF5BE48),
    onTertiary = Color(color = 0xFF402D00),
    tertiaryContainer = Color(color = 0xFFB9890F),
    onTertiaryContainer = Color(color = 0xFF2C1E00),

    error = Color(color = 0xFFFFB4AB),
    onError = Color(color = 0xFF690005),
    errorContainer = Color(color = 0xFF93000A),
    onErrorContainer = Color(color = 0xFFFFDAD6),

    surfaceDim = Color(color = 0xFF18120C),
    surface = Color(color = 0xFF18120C),
    surfaceBright = Color(color = 0xFF3F3830),
    onSurface = Color(color = 0xFFEDE0D4),
    onSurfaceVariant = Color(color = 0xFFD9C3AF),

    surfaceContainerLowest = Color(color = 0xFF130D07),
    surfaceContainerLow = Color(color = 0xFF211A13),
    surfaceContainer = Color(color = 0xFF251E17),
    surfaceContainerHigh = Color(color = 0xFF302921),
    surfaceContainerHighest = Color(color = 0xFF3B332B),

    inverseSurface = Color(color = 0xFFEDE0D4),
    inverseOnSurface = Color(color = 0xFF362F27),
    inversePrimary = Color(color = 0xFF9F4200),

    outline = Color(color = 0xFFA18D7B),
    outlineVariant = Color(color = 0xFF544435),

    scrim = Color.Black,

    info = Color(color = 0xFFBEE6FF),
    onInfo = Color(color = 0xFF002E41),
    infoContainer = Color(color = 0xFF262C40),
    onInfoContainer = Color(color = 0xFFBEE6FF),

    success = Color(color = 0xFFB2D189),
    onSuccess = Color(color = 0xFF1A3305),
    successContainer = Color(color = 0xFF1A3305),
    onSuccessContainer = Color(color = 0xFFB2D189),

    warning = Color(color = 0xFFFEE78A),
    onWarning = Color(color = 0xFF411107),
    warningContainer = Color(color = 0xFF423606),
    onWarningContainer = Color(color = 0xFFFEE78A),
)
