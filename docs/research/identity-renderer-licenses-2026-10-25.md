# Identity renderer asset provenance and licenses

Checked: 2026-10-25 (Asia/Shanghai). Scope: the two bundled JavaScript renderers introduced after the 0.9.7 public export. The active Android source files were read only; this check did not alter either renderer.

## Findings

| Local asset | Verified upstream version | Comparison | License |
|---|---|---|---|
| `app/src/main/assets/JsBarcode.code128.min.js` | JsBarcode 3.12.3 (`v3.12.3`) | Same content after CRLF to LF normalization; raw bytes differ | MIT |
| `app/src/main/assets/qrcode-generator.js` | qrcode-generator 2.0.4 (`js2.0.4`) | Same content after CRLF to LF normalization; raw bytes differ | MIT |

Both renderers are third-party MIT software. They are outside the project author's noncommercial/permission-required license for original project additions. Upstream MIT terms permit use, copying, modification, distribution and commercial use subject to preserving the copyright and permission notice. Preserve each bundled header and the full corresponding license when redistributing these files or substantial portions. The upstream license also contains the warranty disclaimer. These conclusions follow directly from each full upstream license linked below.

No JavaScript logic difference was found in either renderer. The differences consist solely of line endings. This does not establish other application files or binaries as upstream-identical.

## JsBarcode 3.12.3

- Author/copyright holder: Johan Lindell (upstream license copyright 2016).
- [Official tag](https://github.com/lindell/JsBarcode/tree/v3.12.3); [resolved commit](https://github.com/lindell/JsBarcode/commit/fbe17cbff898ae6a4797b4489bd66034d2ae8e7e).
- [Pinned official asset](https://raw.githubusercontent.com/lindell/JsBarcode/fbe17cbff898ae6a4797b4489bd66034d2ae8e7e/dist/barcodes/JsBarcode.code128.min.js).
- [Pinned complete upstream MIT license](https://raw.githubusercontent.com/lindell/JsBarcode/fbe17cbff898ae6a4797b4489bd66034d2ae8e7e/MIT-LICENSE.txt); copied without rewriting to [`LICENSES/MIT-JsBarcode.txt`](../../LICENSES/MIT-JsBarcode.txt).
- Local asset: `app/src/main/assets/JsBarcode.code128.min.js` (27900 bytes; 1 CRLF sequences).
- Upstream asset: `dist/barcodes/JsBarcode.code128.min.js` (27899 bytes; 0 CRLF sequences).
- Local SHA-256: `fd92b99bfa11f59e7f3d02ed712de750d24cea8308993970dc47873caeada4f9`.
- Upstream SHA-256: `7525fc29d3597337d8366b15414871f63b70b967c94fa0c4edb135efe1d6709d`.
- SHA-256 after CRLF to LF normalization (both): `7525fc29d3597337d8366b15414871f63b70b967c94fa0c4edb135efe1d6709d`.
- Copied license SHA-256: `e34674e4ef4ed987f7a21a3040a1bdce83f08cc07eedfacbba1ffe53b0633734` (1083 bytes).

## qrcode-generator 2.0.4

- Author/copyright holder: Kazuhiko Arase (upstream license copyright 2009).
- [Official tag](https://github.com/kazuhikoarase/qrcode-generator/tree/js2.0.4); [resolved commit](https://github.com/kazuhikoarase/qrcode-generator/commit/83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8).
- [Pinned official asset](https://raw.githubusercontent.com/kazuhikoarase/qrcode-generator/83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8/js/dist/qrcode.js).
- [Pinned complete upstream MIT license](https://raw.githubusercontent.com/kazuhikoarase/qrcode-generator/83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8/LICENSE); copied without rewriting to [`LICENSES/MIT-qrcode-generator.txt`](../../LICENSES/MIT-qrcode-generator.txt).
- Local asset: `app/src/main/assets/qrcode-generator.js` (58991 bytes; 2297 CRLF sequences).
- Upstream asset: `js/dist/qrcode.js` (56694 bytes; 0 CRLF sequences).
- Local SHA-256: `55427af126a7fd46b83b7a9557b7ad6a48850b31f2b4a1b32e2289e376dc80b4`.
- Upstream SHA-256: `79ec86f82856005b1c887905cfccfcfbec3821ca61c7fd5a952faa5f778f791c`.
- SHA-256 after CRLF to LF normalization (both): `79ec86f82856005b1c887905cfccfcfbec3821ca61c7fd5a952faa5f778f791c`.
- Copied license SHA-256: `3a850fa5f08101db6f40676c2786e10bd2cd5fff7b12ffdf1e0c434d4e49d90c` (1071 bytes).

The local basename is `qrcode-generator.js`; the official distribution basename is `qrcode.js`. The local header alone does not identify a release. Hash comparison identifies the file as the official `js2.0.4` distribution after line-ending normalization. For comparison, the checked `js1.4.4`, `js1.5.0`, `js1.5.2`, `js2.0.0`, `js2.0.1` and `js2.0.2` distribution files shared SHA-256 `18ae399f81182bc9de916e9c77b195df20cc58d6f2d55a62b085a299f1bf1780` and did not match. The historical `js1.5.1/js/qrcode.js` path returned HTTP 404; it is not used as release evidence.

The bundled upstream header also states that QR Code is a registered trademark of DENSO WAVE INCORPORATED. Preserve that notice; the MIT software license does not establish rights to third-party trademarks. See the [official pinned source header](https://raw.githubusercontent.com/kazuhikoarase/qrcode-generator/83b7e8fe3fddd3b0368dbafd6ce56995bd25e3c8/js/dist/qrcode.js).

## Reproduction and evidence

Download the pinned raw asset URLs above without text conversion, calculate SHA-256, then compare the original bytes. For the content comparison, replace CRLF (`0D 0A`) with LF (`0A`) in both files before hashing. No other normalization was used. The JSON companion records both raw hashes, sizes, line-ending counts, commit identifiers and license hashes.

This is a provenance and license-text inventory, not a claim that other third-party rights have been assigned to the project author. The scope of any commercial permission for original project code must continue to exclude these upstream MIT assets.
