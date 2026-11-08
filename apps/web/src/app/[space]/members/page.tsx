import { MembersView } from '@/views/MembersView'
import { section } from '@/views/section'

const page = section(MembersView, 'members')

export const generateMetadata = page.generateMetadata
export default page.Page
