export type EditorMode = 'editor' | 'split' | 'preview'
export type UploadImage = (file: File, onProgress: (percent: number) => void) => Promise<{ url: string; alt?: string }>
export type Format = 'heading' | 'bold' | 'italic' | 'strike' | 'quote' | 'code' | 'codeblock' | 'link' | 'image' | 'ordered' | 'unordered' | 'table'
