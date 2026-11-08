import { GoalsView } from '@/views/GoalsView'
import { section } from '@/views/section'

const page = section(GoalsView, 'goals')

export const generateMetadata = page.generateMetadata
export default page.Page
