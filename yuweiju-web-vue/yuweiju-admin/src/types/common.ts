export type DefaultImageScene = 'dish' | 'setmeal' | 'general' | (string & {})

export interface DefaultImageQuery {
  scene?: DefaultImageScene
  limit?: number
}

export interface DefaultImageItem {
  name: string
  objectKey: string
  url: string
  scene?: DefaultImageScene
  size?: number
  lastModified?: string
}