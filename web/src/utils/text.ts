function chineseCharCount(value: string) {
  const matches = value.match(/[\u4e00-\u9fff]/g)
  return matches ? matches.length : 0
}

function looksLikeUtf8Mojibake(value: string) {
  return /(?:Ã|Â|Ä|Å|Æ|Ç|È|É|Ê|Ë|Ì|Í|Î|Ï|Ð|Ñ|Ò|Ó|Ô|Õ|Ö|Ø|Ù|Ú|Û|Ü|Ý|Þ|ß|à|á|â|ã|ä|å|æ|ç|è|é|ê|ë|ì|í|î|ï|ð|ñ|ò|ó|ô|õ|ö|ø|ù|ú|û|ü|ý|þ|ÿ)/.test(value)
}

function repairUtf8Mojibake(value: string) {
  try {
    const bytes = Uint8Array.from(Array.from(value).map(char => char.charCodeAt(0) & 0xff))
    return new TextDecoder('utf-8').decode(bytes)
  } catch {
    return value
  }
}

export function normalizeDisplayText(value: string | null | undefined) {
  if (!value) {
    return ''
  }

  if (!looksLikeUtf8Mojibake(value)) {
    return value
  }

  const repaired = repairUtf8Mojibake(value)
  return chineseCharCount(repaired) > chineseCharCount(value) ? repaired : value
}
