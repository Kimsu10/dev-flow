export async function validateImage(file: File, maxBytes: number) {
  if (file.size === 0 || file.size > maxBytes) throw new Error(`이미지는 ${Math.floor(maxBytes / 1024 / 1024)}MB 이하여야 합니다.`)
  const bytes = new Uint8Array(await file.slice(0, 12).arrayBuffer())
  const png = bytes[0] === 137 && bytes[1] === 80 && bytes[2] === 78 && bytes[3] === 71 && bytes[4] === 13 && bytes[5] === 10 && bytes[6] === 26 && bytes[7] === 10
  const jpeg = bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255
  const gif = String.fromCharCode(...bytes.slice(0, 6)).match(/^GIF8[79]a$/)
  const webp = String.fromCharCode(...bytes.slice(0, 4)) === 'RIFF' && String.fromCharCode(...bytes.slice(8, 12)) === 'WEBP'
  const valid = png && file.type === 'image/png' && /\.png$/i.test(file.name)
    || jpeg && file.type === 'image/jpeg' && /\.jpe?g$/i.test(file.name)
    || gif && file.type === 'image/gif' && /\.gif$/i.test(file.name)
    || webp && file.type === 'image/webp' && /\.webp$/i.test(file.name)
  if (!valid) throw new Error('PNG, JPEG, GIF, WebP 파일의 확장자와 실제 형식을 확인해 주세요.')
}
export function imageMarkdown(url: string, alt: string) {
  const parsed = new URL(url, window.location.origin)
  if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('이미지 URL은 HTTP(S) 주소여야 합니다.')
  return `![${alt.replace(/[\[\]\\\r\n]/g, ' ')}](<${parsed.href.replace(/>/g, '%3E')}>)`
}
