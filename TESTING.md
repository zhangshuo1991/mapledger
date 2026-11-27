# Emulator test log (MapLedger v0.1)

## Environment
- Toolchain: /workspace/tools (setup.sh re-creates JDK 21 + SDK; env.sh exports paths). shot helper: /workspace/tools/shot.sh
- AVD ml34: API 34 google_apis x86_64 (has libndk_translation → runs Amap's ARM-only native libs), 720x1600@320dpi.
- KVM: /dev/kvm chmod 666 + vmx present, but vCPU creation oopses in host kernel (`kvm_spurious_fault` in
  kvm_arch_vcpu_create, dmesg) → KVM unusable on this box. MTTCG (`-accel tcg,thread=multi`) boots but netd/zygote
  crash-loop forever. Working config: single-thread TCG, 2 cores, `-prop ro.hw_timeout_multiplier=20`,
  heavy Google packages disabled (gms, vending, gboard, launcher, bluetooth…).
- `adb exec-out screencap` returns black frames for app windows on this setup; use
  `adb emu screenrecord screenshot` (shot.sh) instead.

## Bugs found & fixed
1. Nearby sheet + locate button never appeared: SheetState.anchors was a plain var, offset lambda did not observe it.
   → anchors is now snapshot state. (verified after fix: pending)
2. setPointToCenter could be lost if map controller wasn't ready → anchor stored and applied when controller ready.
3. R8 release: missing com.amap.ams.gnss.* → keep/dontwarn rules.

## Results
(see below, appended as tests run)
4. Nearby list stuck on "正在查找…" forever when the POI request timed out: TimeoutCancellationException is a
   CancellationException and was re-thrown → state never left loading. → handled as an error now (shows retry).
5. Map view creation deferred until after the first frame (heavy native init off the first frame).
6. If location resolved before the deferred map existed, camera stayed at the fallback city → animate when controller ready.

### Run 3 (04:57 UTC)
- Privacy consent screen → 同意 → system location dialog → "While using the app": PASS
- Amap location (mock geo fix 31.2304,121.4737 → GCJ-02 31.22847,121.47822): PASS
- Amap PoiSearch around location: code=1000, 25 POIs (first call ~20 s on TCG emulator, then ~8 s): PASS (key auth OK)

## Run 4 (05:00–05:09 UTC) — debug build from Run 3
- PASS Amap tiles render fully (roads, buildings, POI labels) ~1 min after first launch under TCG.
- PASS tap POI -> amount sheet, auto category 餐饮 from 西餐厅 typecode; keypad 128.5 -> DB 12850 cents.
- PASS 完成 -> sheet closes, month total rolls to ¥128.50, category pin appears, "来过 1 次" badge.
- PASS category override (诚物玩味 购物->娱乐) remembered on reopen; invalid second '.' rejected.
- PASS map drag -> camera idle user=true -> new POI search (25).
- PASS 就记在图钉这里 -> custom place sheet; saved as 生活.
- PASS ledger (totals, category bar, legend, day group), row -> place detail, pin tap -> place detail,
  在地图上看 -> camera to place, 再记一笔 -> amount sheet, back stack sheet->detail->map.
- No FATAL/ANR/auth errors in logcat.
Bugs found -> fixed in code (Run 5 rebuild):
 7. Reverse geocode never called back within 8s -> 12s timeout + log + fallback name "<nearest POI>附近".
 8. Hint said "根据…自动分类" for remembered category when opened from detail -> use hasOverride().
 9. Hint after reopen said "已改为…" -> compare against initialCategory.
10. Category chip row scrolled even when chip visible -> only scroll if index>=3.
11. Ledger pins tiny vs Amap icons -> 22/26/30dp with white ¥ glyph.
12. Legend percent truncation (73/19/6) -> rounding; amount/name spacing; detail rows no longer repeat place name.
13. "0m" -> "就在这"; soft top gradient for status-bar legibility over the map.

## Run 5 (13:23–13:32 Asia/Shanghai on device; box logs UTC) — debug build 05:20 UTC
Tooling: emulator-side grab (`adb emu screenrecord screenshot`) and the image viewer cache were giving
stale frames; tools/shot.sh now uses guest `adb exec-out screencap -p` (live) and a unique view copy.
- PASS cold start: first POI search 22.6s, within new 25s timeout (+1 auto retry) — no error row.
- PASS fix 7: custom pin named "西藏南路附近" (regeo code=1000).
- PASS fix 9/8: reopen remembered place -> "按你上次在这里的选择：娱乐".
- PASS fix 11: ¥ badge pins visible; fix 12: legend 61%/39% sums to 100, amount spacing ok.
- PASS keypad: "." then "00" -> 0.00, 完成 disabled at 0; 58 saved -> toast "已记录 ¥58.00 · 餐饮", total 307.50.
- PASS swipe-delete (red reveal + trash), DB row removed, totals update.
Bugs -> fixed (Run 6 build):
14. Saved toast overlapped the month pill -> moved below the top bar.
15. Delete had no undo -> "已删除 … 撤销" pill (6s).
- PASS dark mode (Run 5b, build 05:35 UTC): `cmd uimode night yes` switches live without relaunch; night map,
  dark sheet/keypad/ledger. Bug 16 found: status-bar icons stayed dark on dark -> fixed (WindowCompat
  insets controller synced to theme in MapLedgerTheme); verified white icons after rebuild.
- PASS rotation: activity is portrait-locked; forcing user_rotation=1 keeps ROTATION_0 and the open amount
  sheet (¥25) intact.
- Process death (kill -9 while backgrounded, relaunch from task): no crash, ledger/overrides intact, but
  Bug 17: camera/list restored at the fallback city because `located` was saved and `center` wasn't ->
  fixed: center is rememberSaveable and the app re-locates on restore when permission is granted.
  (An in-progress amount entry is not restored after process death — by design for v1.)
- PASS fix 17 (build 05:47 UTC): after kill -9 + relaunch the app re-locates (locate -> 31.22847,121.47822).
- Bug 18: first POI request right after a cold start sometimes fails with Amap 1902 (IOException) on the
  emulator network; 重试 recovers. Fix: one automatic retry for 1800–1902, friendlier message "网络不太稳定，点重试".

## Run 6 (14:1x Asia/Shanghai) — build 06:08 UTC (bug 18 retry, undo ellipsis, delete/restore logs)
- PASS bug 18 retry observed on build 05:52: `poi callback code=1902` -> "nearby io error 1902, retrying once" -> code=1000 n=25.
- PASS toast now below the top bar (frame in flow.mp4 / 06b-saved-toast.png).
- Undo pill shows "已删除 … ¥32.00 撤销" (seen in recording); tap timing on TCG emulator is flaky.
- PASS bug 18 cold-start retest (debug 06:08, launch 14:10:41): 1902 after 15.8s -> auto retry -> 25 POIs at 14:11:12, no error row.
- Release slimming: abiFilters arm64-v8a only (-PwithArmv7 adds armeabi-v7a), jniLibs.useLegacyPackaging=true
  (compressed .so), R8 + shrinkResources kept. Previous release (arm64+v7a, stored libs) was 70.3 MB.
- Release slim result: app-release.apk 24.6 MB (arm64-v8a only; libAMapSDK_MAP 34.5 MB raw -> 13.4 MB deflated).
- Emulator issue: after `adb uninstall` the release `adb install` / `pm install` hung in binder (package
  manager stuck; FallbackHome spinning). Framework restart (`stop; start`) to clear it.
- Secrets check (git index): Amap key not found, release password not found, no *.jks/*.keystore/*.pass/*.key/
  local.properties/build outputs tracked. Only the public debug keystore password "android" is in build.gradle.kts.

## Run 7 — signed RELEASE (R8, arm64-only, 24.6 MB) on emulator (arm64 libs via ndk_translation)
- Install: uninstall debug -> pm install (after framework restart) Success; signer SHA-1 5a2334db…a1b9 (release).
- PASS privacy screen (r01), runtime permission dialog (r02), skeleton loading (r02-loading-skeleton).
- PASS Amap with release SHA1: POI code=1000 n=25, map tiles + labels render (r03), location OK on 2nd try.
- Bug 19: first location right after the permission grant timed out (10s) -> default city. Fix: one retry
  before fallback (in code, verified in Run 8).
- Note: Amap prints one authErrLog "SERVICE_NOT_EXIST" at startup; map/search/location all authenticate
  and work, so it is an auxiliary service not enabled for this key (non-blocking). The SDK's own auth log
  prints the key; logcat output is not kept in the repo or reports.
- PASS release two-step flow: 诚物玩味 auto 购物 (chip row not scrolled, fix 10), 66 -> saved, total ¥66.00, pink ¥ pin (r04, r05).
- PASS release ledger + place detail (fix 12: visit rows show 今天 14:46 / 购物) (r06, r07).
- Bug 20: swipe-delete fired onDelete twice, and after 撤销 the restored row stayed in the red dismissed state
  (r08). Fix: fire once per swipe; undo generation resets the row's swipe state. Verify in Run 8.
- PASS (release 06:50) location right after permission grant succeeded first try (14:54:59).
- Bug 21: the map's first camera-idle (created at the default city before the fix arrived) re-searched
  人民广场 and left the camera there. Fix: a non-user idle >150 m from the known centre re-centres instead
  of searching; 在地图上看 updates the centre. Verify in Run 8.

## Run 8 — signed RELEASE 06:57 UTC (24.6 MB)
- PASS bug 21: cold start 15:01 -> locate OK -> nearby 25 at fix; map's initial idle at 31.2304,121.4737
  was ignored (no default-city search) and camera re-centred to 31.22847,121.47822.
- PASS two-step smoke: TAGSIU auto 餐饮, 9 -> saved, total ¥66->¥75, orange pin (r10).
- PASS bug 20: swipe-delete logs one 'deleted expense 2', 撤销 -> 'restored expense 2', row back in normal state, total ¥75 (r11).
- PASS dark mode (release): ledger + map + sheet dark, light status-bar icons, dark map style (r12, r13).
- PASS logcat: no FATAL / AndroidRuntime / ANR entries for the whole Run 8.
- Run 8 RESULT: PASS (signed release build, R8, arm64).
