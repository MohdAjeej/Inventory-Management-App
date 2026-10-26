// Builds the website's translation dictionaries from the Android app's string resources, so the web
// and mobile apps use exactly the same translations.
//
//   node scripts/build-i18n.mjs
//
// Reads  ../android/app/src/main/res/values{,-xx}/strings*.xml
// Writes public/i18n/<lang>.json  →  { exact: { "English": "translation" }, templates: [["English %1$s", "… %1$s …"]] }
import { mkdirSync, readFileSync, readdirSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const res = join(here, "..", "..", "android", "app", "src", "main", "res");
const out = join(here, "..", "public", "i18n");
const LANGS = ["hi", "bn", "ta", "mr", "ar", "ur"];

function decode(raw) {
  return raw
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, "$1")
    .replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#39;/g, "'").replace(/&amp;/g, "&")
    .replace(/\\n/g, "\n").replace(/\\'/g, "'").replace(/\\"/g, '"').replace(/\\@/g, "@").replace(/\\\?/g, "?")
    .replace(/%%/g, "%")
    .trim();
}

function readStrings(dir) {
  const map = new Map();
  if (!existsSync(dir)) return map;
  for (const file of readdirSync(dir).filter((f) => /^strings.*\.xml$/.test(f))) {
    const xml = readFileSync(join(dir, file), "utf8");
    for (const m of xml.matchAll(/<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)) map.set(m[1], decode(m[2]));
  }
  return map;
}

const english = readStrings(join(res, "values"));
if (english.size === 0) {
  console.error(`No English strings found in ${res}/values. Is the Android project next to website/?`);
  process.exit(1);
}
mkdirSync(out, { recursive: true });
for (const lang of LANGS) {
  const tr = readStrings(join(res, `values-${lang}`));
  const exact = {};
  const templates = [];
  for (const [name, en] of english) {
    const t = tr.get(name);
    if (!t || !en) continue;
    if (/%\d+\$[sd]/.test(en)) templates.push([en, t]);
    else exact[en] = t;
  }
  writeFileSync(join(out, `${lang}.json`), JSON.stringify({ exact, templates }));
  console.log(`${lang}: ${Object.keys(exact).length} texts, ${templates.length} templates`);
}
