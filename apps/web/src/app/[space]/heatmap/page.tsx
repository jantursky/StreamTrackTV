import { HeatmapView } from '@/views/HeatmapView'
import { section } from '@/views/section'

const page = section(HeatmapView, 'heatmap')

export const generateMetadata = page.generateMetadata
export default page.Page
