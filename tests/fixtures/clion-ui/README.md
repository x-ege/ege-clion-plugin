Original, unmodified screenshot from CLion GUI startup probe run 37784389082, artifact 11553539443, gui-startup-probe/screen-115.png.

Run: https://github.com/x-ege/ege-clion-plugin/actions/runs/37784389082

SHA256: 7975e25994d9974278be94e4dec47c8d22b06fd96ff9eaea15d575d3f3318c19

This fixture reproduces blank default OCR on a dark first-start User Agreement 1.4 dialog. Raw screenshots remain intact; only OCR input is trimmed, converted to grayscale, inverted and enlarged. No acceptance, license, account or credential data is included.

`data-sharing.png` is the unmodified window-007-0x00200043.png from authorized run 37793769485, artifact 11557552680. SHA256: 10337478d4f1e6d28bd3b1e80cb8d966c46a923c0f349962a59a4108039b017c. Its optional anonymous-statistics prompt was visually audited; only the Don't Send button can be selected. The body pixel hash excludes the bottom 60-pixel button band and must match exactly before input.

`licenses-before-trial.png` is the unmodified window-009-0x004000c3.png from run 37795690126. SHA256: c54e75c2acab201f1d01ffed0e1de25bc3426069e9ff6afa7f52503a2c732da6. Only the Start trial radio option may be selected in the exact audited window, solely to inspect the next page. Login, OAuth and license activation are not implemented.
