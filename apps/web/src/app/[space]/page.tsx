import { OverviewView } from '@/views/OverviewView'
import { section } from '@/views/section'

const page = section(OverviewView, '')

export const generateMetadata = page.generateMetadata
export default page.Page
