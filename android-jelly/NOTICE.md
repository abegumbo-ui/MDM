# Browser (based on Jelly)

This app is the LineageOS **Jelly** browser (https://github.com/LineageOS/android_packages_apps_Jelly,
commit 604fa77181699759290105c53c40557e478dc5f5), Copyright The LineageOS Project, licensed under the Apache License 2.0 (see LICENSES/).

Changes made here (the family dashboard's rules):
- app name, app id (com.familymdm.browser) and signing, so it replaces the old Browser;
- com.familymdm.browser.* : the dashboard connection, filter levels and switches, approved sites,
  YouTube limits, blocked words, picture checking (Guard.java and the helpers next to it);
- hooks in WebViewExt, WebClient and MainActivity that send every address, link and picture through Guard;
- incognito tabs, the "web debugging" setting and app installs from downloads are turned off.

## NudeNet (man/woman detector)

"Cover skin of: women only / men only" uses NudeNet's 320n model (https://github.com/notAI-tech/NudeNet),
licensed under the GNU Affero General Public License v3.0. It is converted for the phone by
.github/workflows/nsfw-model.yml (nudenet320.tflite in the "models" release). The full source of this
app and of the MDM Agent, which also uses it, is public at https://github.com/abegumbo-ui/MDM.
