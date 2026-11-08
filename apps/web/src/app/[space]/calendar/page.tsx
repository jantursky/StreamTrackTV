import { CalendarView } from '@/views/CalendarView'
import { section } from '@/views/section'

const page = section(CalendarView, 'calendar')

export const generateMetadata = page.generateMetadata
export default page.Page
