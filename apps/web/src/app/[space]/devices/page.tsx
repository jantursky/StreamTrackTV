import { DevicesView } from '@/views/DevicesView'
import { section } from '@/views/section'

const page = section(DevicesView, 'devices')

export const generateMetadata = page.generateMetadata
export default page.Page
