// Small helpers for showing money, dates and labels.

export const money = (value) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 })
    .format(Number(value || 0))

// "2026-11-20" -> "20 Nov 2026"
export const formatDate = (iso) => {
  if (!iso) return '-'
  const [year, month, day] = iso.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
}

const FREQUENCY = { MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', HALF_YEARLY: 'Half-yearly', YEARLY: 'Yearly' }
export const frequencyLabel = (value) => FREQUENCY[value] || value

// "12 months" or "20 years"
export const termLabel = (months) =>
  months % 12 === 0 ? `${months / 12} year${months === 12 ? '' : 's'}` : `${months} months`

// Today as YYYY-MM-DD in the user's own time zone (used as the default policy start date).
export const todayISO = () => {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export const firstName = (fullName = '') => fullName.trim().split(/\s+/)[0] || 'there'
