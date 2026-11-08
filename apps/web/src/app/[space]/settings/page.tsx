import { SettingsView } from '@/views/SettingsView'
import { section } from '@/views/section'

const page = section(SettingsView, 'settings')

export const generateMetadata = page.generateMetadata
export default page.Page
