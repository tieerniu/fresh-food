import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import './styles/admin-theme.css'

// 引入 Element Plus
import {
    ElAlert,
    ElButton,
    ElIcon,
    ElImage,
    ElInput,
    ElTag,
    ElTimeline,
    ElTimelineItem
} from 'element-plus'
import 'element-plus/dist/index.css'

// 引入全局样式（如果需要）
const app = createApp(App)

app.use(router)

const baseElementPlusPlugins = [
    ElAlert,
    ElButton,
    ElIcon,
    ElImage,
    ElInput,
    ElTag,
    ElTimeline,
    ElTimelineItem
]

baseElementPlusPlugins.forEach(plugin => app.use(plugin))

let adminElementPlusRegistered = false
const registerAdminElementPlus = async () => {
    if (adminElementPlusRegistered) return

    const {
        ElAside,
        ElCard,
        ElCheckbox,
        ElCol,
        ElContainer,
        ElDatePicker,
        ElDescriptions,
        ElDescriptionsItem,
        ElDialog,
        ElDrawer,
        ElDropdown,
        ElDropdownItem,
        ElDropdownMenu,
        ElEmpty,
        ElForm,
        ElFormItem,
        ElHeader,
        ElInputNumber,
        ElLink,
        ElLoading,
        ElMain,
        ElMenu,
        ElMenuItem,
        ElOption,
        ElPagination,
        ElPopover,
        ElProgress,
        ElRadioButton,
        ElRadioGroup,
        ElRow,
        ElSelect,
        ElSwitch,
        ElTable,
        ElTableColumn,
        ElText,
        ElTooltip
    } = await import('element-plus')

    ;[
        ElAside,
        ElCard,
        ElCheckbox,
        ElCol,
        ElContainer,
        ElDatePicker,
        ElDescriptions,
        ElDescriptionsItem,
        ElDialog,
        ElDrawer,
        ElDropdown,
        ElDropdownItem,
        ElDropdownMenu,
        ElEmpty,
        ElForm,
        ElFormItem,
        ElHeader,
        ElInputNumber,
        ElLink,
        ElLoading,
        ElMain,
        ElMenu,
        ElMenuItem,
        ElOption,
        ElPagination,
        ElPopover,
        ElProgress,
        ElRadioButton,
        ElRadioGroup,
        ElRow,
        ElSelect,
        ElSwitch,
        ElTable,
        ElTableColumn,
        ElText,
        ElTooltip
    ].forEach(plugin => app.use(plugin))

    adminElementPlusRegistered = true
}

router.beforeEach(async (to, from, next) => {
    if (to.path !== '/') {
        await registerAdminElementPlus()
    }
    next()
})

app.mount('#app')
