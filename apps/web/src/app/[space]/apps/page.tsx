import { AppsView } from '@/views/AppsView'
import { section } from '@/views/section'

const page = section(AppsView, 'apps')

export const generateMetadata = page.generateMetadata
export default page.Page
