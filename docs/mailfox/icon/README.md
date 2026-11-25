# MailFox icons

Source: `mailfox_design.jpg` (design "3": metal fox with envelope and padlock, line-art fox for the status bar).

The icons are generated from the design image:

```bash
pip install pillow numpy potracer
python3 lineart.py mailfox_design.jpg <work-dir>     # status bar / monochrome line art -> vector paths
python3 appicon.py mailfox_design.jpg <work-dir>     # launcher icon background + logo PNGs
python3 vectors.py <work-dir> ../../../app-k9mail/src/main/res
```

- App icon: `app-k9mail/src/main/res/mipmap-*/ic_launcher_mailfox_background.png` (adaptive icon),
  `mipmap-*/ic_mailfox_logo.png` (logo, also used as launcher icon before Android 8)
- Status bar icon: `app-k9mail/src/main/res/drawable/ic_mailfox_notification.xml`
- Themed icon (Android 13+): `app-k9mail/src/main/res/drawable/ic_app_logo_monochrome.xml`
- Compose logo: `components/ui/bolt/src/commonMain/composeResources/drawable*/bolt_k9mail_logo.png`
