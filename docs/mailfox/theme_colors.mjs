import { argbFromHex, hexFromArgb, Hct, TonalPalette, DynamicScheme, MaterialDynamicColors } from '@material/material-color-utilities';
const primarySeed = argbFromHex('#D9661F');   // Fuchs-Orange
const secondarySeed = argbFromHex('#5E7A3A'); // Moosgrün
const tertiarySeed = argbFromHex('#D9A21B');  // Herbstgold
const p = Hct.fromInt(primarySeed);
const palettes = {
  primaryPalette: TonalPalette.fromHueAndChroma(p.hue, Math.max(p.chroma, 60)),
  secondaryPalette: TonalPalette.fromHueAndChroma(Hct.fromInt(secondarySeed).hue, 36),
  tertiaryPalette: TonalPalette.fromHueAndChroma(Hct.fromInt(tertiarySeed).hue, 48),
  neutralPalette: TonalPalette.fromHueAndChroma(72, 6),
  neutralVariantPalette: TonalPalette.fromHueAndChroma(66, 11),
};
const names = ['primary','onPrimary','primaryContainer','onPrimaryContainer','secondary','onSecondary','secondaryContainer','onSecondaryContainer','tertiary','onTertiary','tertiaryContainer','onTertiaryContainer','error','onError','errorContainer','onErrorContainer','surfaceDim','surface','surfaceBright','onSurface','onSurfaceVariant','surfaceContainerLowest','surfaceContainerLow','surfaceContainer','surfaceContainerHigh','surfaceContainerHighest','inverseSurface','inverseOnSurface','inversePrimary','outline','outlineVariant','primaryFixed','onPrimaryFixed','primaryFixedDim','onPrimaryFixedVariant','secondaryFixed','onSecondaryFixed','secondaryFixedDim','onSecondaryFixedVariant','tertiaryFixed','onTertiaryFixed','tertiaryFixedDim','onTertiaryFixedVariant'];
const out = {};
for (const isDark of [false, true]) {
  const scheme = new DynamicScheme({ sourceColorArgb: primarySeed, variant: 6, contrastLevel: 0, isDark, ...palettes });
  const o = {};
  for (const n of names) o[n] = hexFromArgb(MaterialDynamicColors[n].getArgb(scheme)).toUpperCase();
  out[isDark ? 'dark' : 'light'] = o;
}
console.log(JSON.stringify(out, null, 1));
