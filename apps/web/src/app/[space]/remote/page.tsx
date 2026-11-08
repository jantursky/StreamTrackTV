import { RemoteView } from '@/views/RemoteView'
import { section } from '@/views/section'

const page = section(RemoteView, 'remote')

export const generateMetadata = page.generateMetadata
export default page.Page
