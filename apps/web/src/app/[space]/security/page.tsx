import { SecurityView } from '@/views/SecurityView'
import { section } from '@/views/section'

const page = section(SecurityView, 'security')

export const generateMetadata = page.generateMetadata
export default page.Page
