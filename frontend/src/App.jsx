import { useEffect, useMemo, useState } from 'react'
import {
  Activity, ArrowDownRight, ArrowRight, ArrowUpRight, BadgeCheck, Bell, BookOpenCheck,
  CalendarDays, Check, ChevronDown, ChevronRight, CircleHelp, Clock3, CreditCard,
  FileSearch, HeartPulse, House, LogOut, Menu, Plus, Search, Shield, ShieldCheck,
  Sparkles, WalletCards, X,
} from 'lucide-react'
import api, { messageFromError } from './api'

const money = (value, digits = 0) => new Intl.NumberFormat('en-IN', {
  style: 'currency', currency: 'INR', maximumFractionDigits: digits, minimumFractionDigits: digits,
}).format(Number(value || 0))
const date = (value) => value ? new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }).format(new Date(`${value}T00:00:00`)) : '—'
const frequency = (value) => ({ MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', HALF_YEARLY: 'Half-yearly', YEARLY: 'Yearly' }[value] || value)
const premiumCount = (product) => Math.ceil(product.termMonths / ({ MONTHLY: 1, QUARTERLY: 3, HALF_YEARLY: 6, YEARLY: 12 }[product.premiumFrequency] || 12))
const maturityDateFor = (start, months) => {
  const result = new Date(`${start}T00:00:00`)
  const day = result.getDate()
  result.setDate(1)
  result.setMonth(result.getMonth() + months)
  result.setDate(Math.min(day, new Date(result.getFullYear(), result.getMonth() + 1, 0).getDate()))
  return result.toISOString().slice(0, 10)
}
const shortName = (name = '') => name.trim().split(/\s+/)[0] || 'there'

function App() {
  const [customer, setCustomer] = useState(null)
  const [checkingSession, setCheckingSession] = useState(true)
  const [activeTab, setActiveTab] = useState('overview')
  const [mobileMenu, setMobileMenu] = useState(false)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [policies, setPolicies] = useState([])
  const [products, setProducts] = useState([])
  const [allProducts, setAllProducts] = useState([])
  const [recommendations, setRecommendations] = useState([])
  const [recommendationRequestId, setRecommendationRequestId] = useState(null)
  const [recommendationForm, setRecommendationForm] = useState({ goals: '', preferredTerm: '', preferredPolicyType: '' })
  const [registrationProduct, setRegistrationProduct] = useState(null)
  const [registrationRecommendationId, setRegistrationRecommendationId] = useState(null)
  const [registrationDate, setRegistrationDate] = useState(new Date().toISOString().slice(0, 10))
  const [paymentPolicy, setPaymentPolicy] = useState('')
  const [paymentDetails, setPaymentDetails] = useState(null)
  const [selectedInstallments, setSelectedInstallments] = useState({})
  const [paymentMethod, setPaymentMethod] = useState('UPI')
  const [searchForm, setSearchForm] = useState({ policyNumber: '', customerName: '', dateOfBirth: '' })
  const [searchResults, setSearchResults] = useState(null)
  const [query, setQuery] = useState('')

  useEffect(() => {
    const onExpired = () => { setCustomer(null); setNotice({ type: 'error', text: 'Your session expired. Please sign in again.' }) }
    window.addEventListener('healthdekho:session-expired', onExpired)
    const token = localStorage.getItem('healthdekho-token')
    if (!token) { setCheckingSession(false); return () => window.removeEventListener('healthdekho:session-expired', onExpired) }
    api.get('/auth/me').then(({ data }) => setCustomer(data)).catch(() => localStorage.removeItem('healthdekho-token')).finally(() => setCheckingSession(false))
    return () => window.removeEventListener('healthdekho:session-expired', onExpired)
  }, [])

  useEffect(() => {
    if (!notice) return undefined
    const timer = window.setTimeout(() => setNotice(null), 4200)
    return () => window.clearTimeout(timer)
  }, [notice])

  useEffect(() => {
    if (!customer) return
    Promise.all([api.get('/policies'), api.get('/products/available'), api.get('/products')])
      .then(([myPolicies, available, catalog]) => {
        setPolicies(myPolicies.data)
        setProducts(available.data)
        setAllProducts(catalog.data)
      })
      .catch((error) => setNotice({ type: 'error', text: messageFromError(error) }))
  }, [customer])

  const filteredProducts = useMemo(() => {
    const term = query.toLowerCase().trim()
    if (!term) return products
    return products.filter((product) => `${product.policyName} ${product.policyType} ${product.productCode}`.toLowerCase().includes(term))
  }, [products, query])

  const totalCover = policies.reduce((sum, item) => sum + Number(item.sumInsured), 0)
  const outstandingTotal = policies.reduce((sum, item) => sum + Number(item.remainingPremium), 0)

  async function signIn(event) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    setBusy(true)
    try {
      const { data } = await api.post('/auth/login', { email: form.get('email'), password: form.get('password') })
      localStorage.setItem('healthdekho-token', data.token)
      setCustomer(data.customer)
      setNotice({ type: 'success', text: `Welcome back, ${shortName(data.customer.fullName)}.` })
    } catch (error) { setNotice({ type: 'error', text: messageFromError(error) }) }
    finally { setBusy(false) }
  }

  function signOut() {
    localStorage.removeItem('healthdekho-token')
    setCustomer(null); setPolicies([]); setProducts([]); setPaymentDetails(null)
    setNotice({ type: 'success', text: 'You have signed out securely.' })
  }

  async function refreshPortfolio() {
    const [policyResult, productResult] = await Promise.all([api.get('/policies'), api.get('/products/available')])
    setPolicies(policyResult.data); setProducts(productResult.data)
  }

  async function registerPolicy(event) {
    event.preventDefault()
    setBusy(true)
    try {
      const { data } = await api.post('/policies', { productCode: registrationProduct.productCode, startDate: registrationDate, recommendationRequestId: registrationRecommendationId })
      await refreshPortfolio()
      setRegistrationProduct(null)
      setRegistrationRecommendationId(null)
      setNotice({ type: 'success', text: `Your policy ${data.policyNumber} is active.` })
      setActiveTab('overview')
    } catch (error) { setNotice({ type: 'error', text: messageFromError(error) }) }
    finally { setBusy(false) }
  }

  async function generateRecommendations(event) {
    event.preventDefault(); setBusy(true)
    try {
      const { data } = await api.post('/recommendations', recommendationForm)
      setRecommendations(data.recommendations)
      setRecommendationRequestId(data.requestId)
      setNotice({ type: 'success', text: 'Your personalized shortlist is ready.' })
    } catch (error) { setNotice({ type: 'error', text: messageFromError(error) }) }
    finally { setBusy(false) }
  }

  async function loadPaymentPolicy(policyNumber) {
    setPaymentPolicy(policyNumber); setPaymentDetails(null); setSelectedInstallments({})
    if (!policyNumber) return
    setBusy(true)
    try { const { data } = await api.get(`/policies/${encodeURIComponent(policyNumber)}`); setPaymentDetails(data) }
    catch (error) { setNotice({ type: 'error', text: messageFromError(error) }) }
    finally { setBusy(false) }
  }

  function toggleInstallment(installment) {
    setSelectedInstallments((current) => {
      const next = { ...current }
      if (next[installment.id]) delete next[installment.id]
      else next[installment.id] = installment.remainingAmount
      return next
    })
  }

  async function submitPayments(event) {
    event.preventDefault(); setBusy(true)
    try {
      const payments = Object.entries(selectedInstallments).map(([installmentId, amount]) => ({ installmentId: Number(installmentId), amount: Number(amount) }))
      const { data } = await api.post(`/policies/${encodeURIComponent(paymentPolicy)}/payments`, { payments, paymentMethod })
      await loadPaymentPolicy(paymentPolicy)
      await refreshPortfolio()
      setNotice({ type: 'success', text: `${data.length} payment${data.length === 1 ? '' : 's'} processed successfully.` })
    } catch (error) { setNotice({ type: 'error', text: messageFromError(error) }) }
    finally { setBusy(false) }
  }

  async function searchPolicies(event) {
    event.preventDefault(); setBusy(true)
    try {
      const params = Object.fromEntries(Object.entries(searchForm).filter(([, value]) => value.trim()))
      const { data } = await api.get('/policies/search', { params })
      setSearchResults(data)
      if (!data.length) setNotice({ type: 'info', text: 'No policies matched your search.' })
    } catch (error) { setNotice({ type: 'error', text: messageFromError(error) }) }
    finally { setBusy(false) }
  }

  if (checkingSession) return <div className="splash"><div className="brand-mark"><ShieldCheck size={23} /></div><span>Securely opening your account…</span></div>
  if (!customer) return <LoginScreen onSubmit={signIn} busy={busy} notice={notice} />

  const navItems = [
    { id: 'overview', label: 'Overview', icon: House },
    { id: 'policies', label: 'My policies', icon: ShieldCheck, count: policies.length },
    { id: 'explore', label: 'Explore plans', icon: HeartPulse },
    { id: 'recommend', label: 'For you', icon: Sparkles, badge: 'AI' },
    { id: 'payments', label: 'Premiums', icon: WalletCards },
    { id: 'search', label: 'Policy search', icon: FileSearch },
  ]

  return <div className="app-shell">
    <aside className={`sidebar ${mobileMenu ? 'sidebar-open' : ''}`}>
      <a className="brand" href="#overview" onClick={(e) => { e.preventDefault(); setActiveTab('overview') }}>
        <span className="brand-mark"><ShieldCheck size={22} strokeWidth={2.2} /></span>
        <span className="brand-name">health<span>dekho</span><small>INSURANCE, MADE CLEAR</small></span>
      </a>
      <div className="workspace-label">YOUR SPACE</div>
      <nav className="side-nav" aria-label="Main navigation">
        {navItems.map(({ id, label, icon: Icon, count, badge }) => <button key={id} className={`nav-item ${activeTab === id ? 'active' : ''}`} onClick={() => { setActiveTab(id); setMobileMenu(false) }}>
          <Icon size={18} strokeWidth={1.9} /><span>{label}</span>{count !== undefined && <span className="nav-count">{count}</span>}{badge && <span className="nav-badge">{badge}</span>}
        </button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="help-card"><span className="help-icon"><CircleHelp size={17} /></span><strong>Need a hand?</strong><p>We’re here to help you find the right cover.</p><button onClick={() => setNotice({ type: 'info', text: 'Please contact your HealthDekho support team for assistance.' })}>Get support <ArrowRight size={13} /></button></div>
        <button className="profile-row" onClick={signOut}><div className="avatar">{customer.fullName.split(' ').map((part) => part[0]).slice(0, 2).join('')}</div><span className="profile-meta"><strong>{customer.fullName}</strong><small>Customer account</small></span><LogOut size={16} className="profile-logout" /></button>
      </div>
    </aside>

    <main className="main-area">
      <header className="topbar">
        <button className="mobile-menu-button" aria-label="Open navigation" onClick={() => setMobileMenu((open) => !open)}><Menu size={20} /></button>
        <div className="breadcrumb"><span>My account</span><ChevronRight size={14} /><strong>{navItems.find((item) => item.id === activeTab)?.label}</strong></div>
        <div className="topbar-right"><span className="secure-pill"><span className="secure-dot" /> Secure account</span><button className="icon-button" title="Notifications" onClick={() => setNotice({ type: 'info', text: 'You are all caught up.' })}><Bell size={18} /><i /></button><div className="top-avatar">{customer.fullName.split(' ').map((part) => part[0]).slice(0, 2).join('')}</div></div>
      </header>

      <div className="content-wrap">
        {activeTab === 'overview' && <>
          <section className="welcome-row"><div><div className="eyebrow"><span className="eyebrow-dot" /> YOUR HEALTHDEKHO DASHBOARD</div><h1>Good morning, {shortName(customer.fullName)}<span className="wave">.</span></h1><p className="muted">A clear view of the coverage you’ve built and what comes next.</p></div><button className="button button-dark" onClick={() => setActiveTab('explore')}><Plus size={16} /> Explore a plan</button></section>
          <section className="stat-grid">
            <StatCard label="Active policies" value={policies.length.toString().padStart(2, '0')} note={policies.length ? 'Your cover, all in one place' : 'Your cover starts here'} icon={Shield} tone="green" />
            <StatCard label="Total coverage" value={money(totalCover)} note="Across your registered plans" icon={Activity} tone="blue" />
            <StatCard label="Premium remaining" value={money(outstandingTotal)} note="Across active schedules" icon={WalletCards} tone="amber" />
          </section>
          <section className="dashboard-grid">
            <div className="panel portfolio-panel"><div className="panel-heading"><div><div className="eyebrow">YOUR PORTFOLIO</div><h2>My protection</h2></div><button className="text-button" onClick={() => setActiveTab('policies')}>View all <ArrowRight size={14} /></button></div>
              {policies.length ? <div className="policy-list">{policies.slice(0, 3).map((policy) => <PolicyMini key={policy.policyNumber} policy={policy} onClick={() => { setPaymentPolicy(policy.policyNumber); setActiveTab('payments'); loadPaymentPolicy(policy.policyNumber) }} />)}</div> : <EmptyState icon={Shield} title="Your coverage story starts here" description="Browse our plans and register your first policy in a few simple steps." action="Explore plans" onAction={() => setActiveTab('explore')} />}
            </div>
            <div className="panel spotlight-panel"><div className="spotlight-top"><span className="spotlight-icon"><Sparkles size={18} /></span><span className="spotlight-label">A LITTLE HELP FROM AI</span></div><h2>A plan that feels<br />like it was made for you.</h2><p>Tell us what matters. Get a thoughtful shortlist from our active plans, matched to your age and goals.</p><button className="button button-light" onClick={() => setActiveTab('recommend')}>Find my fit <ArrowRight size={15} /></button><div className="spotlight-art"><div className="art-ring ring-one"/><div className="art-ring ring-two"/><div className="art-shield"><ShieldCheck size={33} /></div><span className="art-spark spark-a">✦</span><span className="art-spark spark-b">✧</span></div></div>
          </section>
          <section className="panel quick-panel"><div className="panel-heading"><div><div className="eyebrow">YOUR NEXT STEP</div><h2>Keep things moving</h2></div></div><div className="quick-actions"><QuickAction icon={HeartPulse} title="Explore coverage" text="See plans that fit your needs" action="Browse plans" onClick={() => setActiveTab('explore')} /><QuickAction icon={CreditCard} title="Stay up to date" text="Check your premium schedule" action="View payments" onClick={() => setActiveTab('payments')} /><QuickAction icon={BookOpenCheck} title="Find a policy" text="Search your policy records" action="Search now" onClick={() => setActiveTab('search')} /></div></section>
        </>}

        {activeTab === 'policies' && <>
          <PageTitle kicker="YOUR COVER, ALL TOGETHER" title="My policies" description="Track your cover, important dates and premium progress." action={<button className="button button-dark" onClick={() => setActiveTab('explore')}><Plus size={16} /> Add coverage</button>} />
          <div className="panel table-panel">{policies.length ? <div className="table-scroll"><table><thead><tr><th>Policy</th><th>Type</th><th>Term</th><th>Started</th><th>Matures</th><th>Premium</th><th>Cover amount</th><th>Status</th></tr></thead><tbody>{policies.map((policy) => <tr key={policy.policyNumber}><td><strong>{policy.policyName}</strong><small className="table-sub">{policy.policyNumber}</small></td><td><span className="type-chip">{policy.policyType}</span></td><td>{Math.round(policy.termMonths / 12)} years</td><td>{date(policy.startDate)}</td><td>{date(policy.maturityDate)}</td><td>{money(policy.premiumAmount)}<small className="table-sub">{frequency(policy.premiumFrequency)}</small></td><td className="amount-strong">{money(policy.sumInsured)}</td><td><span className="status-pill status-active"><i /> {policy.status}</span></td></tr>)}</tbody></table></div> : <EmptyState icon={Shield} title="No policies yet" description="Once you register a plan, all your coverage details will appear here." action="Explore plans" onAction={() => setActiveTab('explore')} />}</div>
        </>}

        {activeTab === 'explore' && <>
          <PageTitle kicker="A GOOD PLACE TO START" title="Explore plans" description="Simple options, designed to protect what matters to you." action={<label className="search-box"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search plans" aria-label="Search insurance plans" /></label>} />
          <div className="catalog-note"><ShieldCheck size={17} /><span>Showing active products you haven’t registered yet.</span><span className="catalog-count">{filteredProducts.length} plans</span></div>
          {filteredProducts.length ? <div className="product-grid">{filteredProducts.map((product) => <ProductCard key={product.productCode} product={product} onRegister={() => { setRegistrationRecommendationId(null); setRegistrationProduct(product); setRegistrationDate(new Date().toISOString().slice(0, 10)) }} />)}</div> : <EmptyState icon={Search} title="No plans found" description="Try another name or policy type." action="Clear search" onAction={() => setQuery('')} />}
        </>}

        {activeTab === 'recommend' && <>
          <PageTitle kicker="THOUGHTFUL COVER, PERSONALIZED" title="Plans for you" description="A few details help us make a more useful shortlist. Recommendations are guidance, not financial advice." />
          <div className="recommend-layout">
            <section className="panel recommend-form-panel"><div className="form-kicker"><Sparkles size={16} /> PERSONALIZED SHORTLIST</div><h2>What matters most to you?</h2><p className="muted">We’ll compare your preferences against plans you’re eligible for and haven’t registered.</p><form className="stack-form" onSubmit={generateRecommendations}><label>Your goals or priorities<textarea value={recommendationForm.goals} onChange={(e) => setRecommendationForm({ ...recommendationForm, goals: e.target.value })} placeholder="For example: health cover for my family, long-term income protection…" rows="4" maxLength="1000" /></label><label>Preferred policy type<select value={recommendationForm.preferredPolicyType} onChange={(e) => setRecommendationForm({ ...recommendationForm, preferredPolicyType: e.target.value })}><option value="">No preference</option><option value="HEALTH">Health</option><option value="TERM">Term life</option><option value="SAVINGS">Savings</option><option value="ACCIDENT">Personal accident</option></select></label><label>Preferred term<input value={recommendationForm.preferredTerm} onChange={(e) => setRecommendationForm({ ...recommendationForm, preferredTerm: e.target.value })} placeholder="e.g. 10 years" maxLength="120" /></label><div className="age-note"><CalendarDays size={16} /><span>We use the date of birth in your profile to confirm age eligibility.</span></div><button className="button button-dark full-button" disabled={busy}><Sparkles size={16} /> {busy ? 'Finding your fit…' : 'Get recommendations'} <ArrowRight size={15} /></button></form></section>
            <aside className="recommend-aside"><div className="trust-card"><span className="trust-icon"><ShieldCheck size={18} /></span><strong>Grounded in real plans</strong><p>Every suggestion is checked against HealthDekho’s active product catalog and your age eligibility.</p></div><div className="trust-card"><span className="trust-icon trust-icon-peach"><BadgeCheck size={18} /></span><strong>You stay in control</strong><p>Compare coverage, terms and premiums. Nothing is registered until you choose it.</p></div><div className="quiet-note"><Sparkles size={15} /><span>AI can help explain options, but it never sets premiums or replaces your own judgement.</span></div></aside>
          </div>
          {recommendations.length > 0 && <section className="recommend-results"><div className="panel-heading"><div><div className="eyebrow">YOUR SHORTLIST</div><h2>Worth a closer look</h2></div><span className="result-count">{recommendations.length} suggestions</span></div><div className="product-grid">{recommendations.map((item) => { const product = allProducts.find((candidate) => candidate.productCode === item.productCode) || item; return <ProductCard key={item.productCode} product={product} recommendation={item} onRegister={() => { setRegistrationRecommendationId(recommendationRequestId); setRegistrationProduct(product); setRegistrationDate(new Date().toISOString().slice(0, 10)) }} /> })}</div></section>}
        </>}

        {activeTab === 'payments' && <>
          <PageTitle kicker="STAY ON TOP OF YOUR COVER" title="Premiums & payments" description="Review scheduled installments and securely record a payment against your policy." />
          <div className="panel payment-picker"><label htmlFor="policy-picker">Choose a policy</label><div className="select-wrap"><Shield size={17} /><select id="policy-picker" value={paymentPolicy} onChange={(event) => loadPaymentPolicy(event.target.value)}><option value="">Select one of your policies</option>{policies.map((policy) => <option key={policy.policyNumber} value={policy.policyNumber}>{policy.policyName} · {policy.policyNumber}</option>)}</select><ChevronDown size={16} /></div></div>
          {!paymentPolicy && <EmptyState icon={WalletCards} title="Your premium schedule awaits" description="Choose a registered policy above to review installments and payment history." />}
          {paymentDetails && <>
            <div className="payment-summary-grid"><SummaryTile label="Total premium" value={money(paymentDetails.policy.totalPremiumPayable)} /><SummaryTile label="Paid so far" value={money(paymentDetails.policy.totalPremiumPaid)} /><SummaryTile label="Still to pay" value={money(paymentDetails.policy.remainingPremium)} highlight /></div>
            <section className="panel table-panel installment-panel"><div className="panel-heading"><div><div className="eyebrow">PREMIUM SCHEDULE</div><h2>Installments</h2></div><span className="schedule-frequency">{frequency(paymentDetails.policy.premiumFrequency)} · {paymentDetails.installments.length} total</span></div>
              {paymentDetails.installments.length ? <form onSubmit={submitPayments}><div className="table-scroll"><table><thead><tr><th className="check-col">Pay</th><th>Installment</th><th>Due date</th><th>Grace ends</th><th>Scheduled</th><th>Paid</th><th>Balance</th><th>Status</th></tr></thead><tbody>{paymentDetails.installments.map((item) => { const canPay = item.remainingAmount > 0; return <tr key={item.id} className={canPay ? '' : 'row-paid'}><td>{canPay ? <input type="checkbox" checked={Boolean(selectedInstallments[item.id])} onChange={() => toggleInstallment(item)} aria-label={`Select installment ${item.installmentNumber}`} /> : <Check className="paid-check" size={16} />}</td><td><strong>#{String(item.installmentNumber).padStart(2, '0')}</strong></td><td>{date(item.dueDate)}</td><td>{date(item.gracePeriodEndDate)}</td><td>{money(item.scheduledAmount)}</td><td>{money(item.paidAmount)}</td><td className="amount-strong">{money(item.remainingAmount)}</td><td><span className={`status-pill ${item.paymentStatus === 'PAID' ? 'status-active' : item.paymentStatus === 'OVERDUE' ? 'status-overdue' : 'status-pending'}`}><i />{item.paymentStatus.replace('_', ' ')}</span></td></tr>})}</tbody></table></div>
                {Object.keys(selectedInstallments).length > 0 && <><div className="selected-payment-list">{Object.entries(selectedInstallments).map(([installmentId, amount]) => { const item = paymentDetails.installments.find((row) => row.id === Number(installmentId)); return <label key={installmentId}>Installment #{item?.installmentNumber}<span>Balance {money(item?.remainingAmount)}</span><input type="number" min="0.01" max={item?.remainingAmount} step="0.01" value={amount} onChange={(event) => setSelectedInstallments((current) => ({ ...current, [installmentId]: event.target.value }))} aria-label={`Payment amount for installment ${item?.installmentNumber}`} required /></label> })}</div><div className="payment-action-bar"><div><strong>{Object.keys(selectedInstallments).length} selected</strong><span>Partial payments are supported. A 10% penalty applies after the grace period.</span></div><div className="payment-controls"><select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value)} aria-label="Payment method"><option value="UPI">UPI</option><option value="CARD">Card</option><option value="NET_BANKING">Net banking</option><option value="BANK_TRANSFER">Bank transfer</option></select><button type="submit" className="button button-dark" disabled={busy}>{busy ? 'Processing…' : 'Record payment'} <ArrowRight size={15} /></button></div></div></>}
              </form> : <p className="empty-inline">No installments were found for this policy.</p>}
            </section>
            {paymentDetails.paymentHistory.length > 0 && <section className="panel history-panel"><div className="panel-heading"><div><div className="eyebrow">TRANSACTION RECORD</div><h2>Payment history</h2></div></div><div className="history-list">{paymentDetails.paymentHistory.map((payment) => <div className="history-row" key={payment.transactionReference}><span className="history-icon"><Check size={15} /></span><div className="history-main"><strong>{payment.transactionReference}</strong><small>Installment #{payment.installmentNumber} · {payment.paymentMethod.replace('_', ' ')}</small></div><span className="history-date">{date(payment.paymentDate)}</span><strong className="amount-strong">{money(payment.chargedAmount)}</strong>{Number(payment.penaltyAmount) > 0 && <span className="penalty-tag">includes {money(payment.penaltyAmount)} penalty</span>}</div>)}</div></section>}
          </>}
        </>}

        {activeTab === 'search' && <>
          <PageTitle kicker="YOUR RECORDS, WITHIN REACH" title="Find a policy" description="Search your own policy records by policy number, name or date of birth." />
          <form className="panel policy-search-form" onSubmit={searchPolicies}><label>Policy identifier<input value={searchForm.policyNumber} onChange={(e) => setSearchForm({ ...searchForm, policyNumber: e.target.value })} placeholder="e.g. HDP-2026-0001" /></label><label>Customer name<input value={searchForm.customerName} onChange={(e) => setSearchForm({ ...searchForm, customerName: e.target.value })} placeholder={customer.fullName} /></label><label>Date of birth<input type="date" value={searchForm.dateOfBirth} onChange={(e) => setSearchForm({ ...searchForm, dateOfBirth: e.target.value })} /></label><button className="button button-dark" disabled={busy}><Search size={16} /> Search policies</button></form>
          {searchResults !== null && <section className="panel table-panel search-results"><div className="panel-heading"><div><div className="eyebrow">SEARCH RESULTS</div><h2>{searchResults.length} matching {searchResults.length === 1 ? 'policy' : 'policies'}</h2></div></div>{searchResults.length ? <div className="table-scroll"><table><thead><tr><th>Policy</th><th>Customer</th><th>Date of birth</th><th>Type</th><th>Started</th><th>Premium total</th><th>Paid</th><th>Remaining</th><th>Status</th></tr></thead><tbody>{searchResults.map((policy) => <tr key={policy.policyNumber}><td><strong>{policy.policyName}</strong><small className="table-sub">{policy.policyNumber}</small></td><td>{policy.customerName}</td><td>{date(policy.dateOfBirth)}</td><td><span className="type-chip">{policy.policyType}</span></td><td>{date(policy.startDate)}</td><td>{money(policy.totalPremiumPayable)}</td><td>{money(policy.totalPremiumPaid)}</td><td>{money(policy.remainingPremium)}</td><td><span className={`status-pill ${policy.paymentStatus === 'OVERDUE' ? 'status-overdue' : policy.paymentStatus === 'PAID' ? 'status-active' : 'status-pending'}`}><i />{policy.paymentStatus}</span></td></tr>)}</tbody></table></div> : <EmptyState icon={FileSearch} title="No matches this time" description="Try a different identifier or clear one of your filters." action="Clear search" onAction={() => { setSearchForm({ policyNumber: '', customerName: '', dateOfBirth: '' }); setSearchResults(null) }} />}</section>}
        </>}

        <footer className="app-footer"><span>© 2026 HealthDekho</span><span>Take care of what matters. <HeartPulse size={13} /></span><a href="#support" onClick={(e) => { e.preventDefault(); setNotice({ type: 'info', text: 'For account support, contact HealthDekho customer care.' }) }}>Help & support</a></footer>
      </div>
    </main>
    {mobileMenu && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMobileMenu(false)} />}
    {notice && customer && <div className={`toast toast-${notice.type}`} role="status"><span className="toast-icon">{notice.type === 'success' ? <Check size={16} /> : notice.type === 'error' ? <X size={16} /> : <CircleHelp size={16} />}</span>{notice.text}<button aria-label="Dismiss notification" onClick={() => setNotice(null)}><X size={15} /></button></div>}
    {registrationProduct && <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setRegistrationProduct(null) }}><div className="modal" role="dialog" aria-modal="true" aria-labelledby="register-title"><button className="modal-close" aria-label="Close" onClick={() => setRegistrationProduct(null)}><X size={19} /></button><div className="modal-icon"><ShieldCheck size={22} /></div><div className="eyebrow">READY WHEN YOU ARE</div><h2 id="register-title">Register your policy</h2><p className="muted">Review your selected plan. We’ll calculate the premium schedule for you.</p><div className="selected-plan"><div><strong>{registrationProduct.policyName}</strong><small>{registrationProduct.productCode} · {registrationProduct.policyType}</small></div><span>{money(registrationProduct.sumInsured)}<small>cover</small></span></div><div className="plan-details-grid"><div><small>Premium frequency</small><strong>{money(registrationProduct.premiumAmount)} / {frequency(registrationProduct.premiumFrequency).toLowerCase()}</strong></div><div><small>Policy term</small><strong>{registrationProduct.termMonths / 12} years</strong></div><div><small>Total premium payable</small><strong>{money(Number(registrationProduct.premiumAmount) * premiumCount(registrationProduct))}</strong></div><div><small>Maturity date</small><strong>{date(maturityDateFor(registrationDate, registrationProduct.termMonths))}</strong></div><div><small>Customer · DOB</small><strong>{customer.fullName} · {date(customer.dateOfBirth)}</strong></div><div><small>Contact</small><strong>{customer.phone}</strong></div></div><form className="stack-form" onSubmit={registerPolicy}><label>Policy start date<input type="date" value={registrationDate} max={new Date().toISOString().slice(0, 10)} onChange={(e) => setRegistrationDate(e.target.value)} required /></label><div className="modal-actions"><button type="button" className="button button-quiet" onClick={() => setRegistrationProduct(null)}>Not now</button><button className="button button-dark" disabled={busy}>{busy ? 'Registering…' : 'Confirm & register'} <ArrowRight size={15} /></button></div></form><div className="modal-footnote"><ShieldCheck size={14} /> Eligibility is confirmed securely before registration.</div></div></div>}
  </div>
}

function LoginScreen({ onSubmit, busy, notice }) {
  return <div className="login-page"><div className="login-decor decor-one"/><div className="login-decor decor-two"/><div className="login-left"><a className="brand login-brand" href="#home"><span className="brand-mark"><ShieldCheck size={22} /></span><span className="brand-name">health<span>dekho</span><small>INSURANCE, MADE CLEAR</small></span></a><div className="login-message"><div className="eyebrow"><span className="eyebrow-dot" /> YOUR COVER, YOUR WAY</div><h1>Feel good about<br />what’s covered.</h1><p>See your policies, stay on top of premiums and find cover that fits your life — all in one clear place.</p><div className="login-proof"><span><Check size={15} /> Your policies, in one place</span><span><Check size={15} /> Helpful, human-first recommendations</span><span><Check size={15} /> Secure access to your account</span></div></div><div className="login-left-footer">© 2026 HealthDekho · Here for what matters</div></div><main className="login-main"><div className="login-card"><div className="login-card-top"><span className="login-icon"><ShieldCheck size={21} /></span><span className="login-lock"><Shield size={13} /> SECURE SIGN IN</span></div><div className="eyebrow">WELCOME BACK</div><h2>Sign in to your account</h2><p className="muted">Your insurance, right where you need it.</p>{notice && <div className={`login-alert ${notice.type}`}>{notice.text}</div>}<form className="stack-form login-form" onSubmit={onSubmit}><label>Email address<input type="email" name="email" autoComplete="username" placeholder="you@example.com" required /></label><label>Password<input type="password" name="password" autoComplete="current-password" placeholder="Enter your password" required /></label><button className="button button-dark full-button" disabled={busy}>{busy ? 'Signing in…' : 'Sign in securely'} <ArrowRight size={16} /></button></form><div className="login-divider"><span /> <small>DEMO ACCESS</small> <span /></div><div className="demo-hint"><span>Sample account</span><strong>aarav.sharma@example.com</strong><span>Password: HealthDekho123!</span></div><div className="login-footnote"><ShieldCheck size={15} /> Your details are protected with secure sign-in.</div></div><div className="login-help">New to HealthDekho? <span>Ask your advisor to set up an account.</span></div></main></div>
}

function StatCard({ label, value, note, icon: Icon, tone }) {
  return <div className="stat-card"><div className={`stat-icon ${tone}`}><Icon size={18} /></div><span className="stat-label">{label}</span><strong className="stat-value">{value}</strong><div className="stat-note"><span className={`stat-trend ${tone}`}><ArrowUpRight size={13} /></span>{note}</div></div>
}
function PageTitle({ kicker, title, description, action }) { return <section className="page-title-row"><div><div className="eyebrow">{kicker}</div><h1>{title}</h1><p className="muted">{description}</p></div>{action && <div className="page-action">{action}</div>}</section> }
function PolicyMini({ policy, onClick }) {
  return <button className="policy-mini" onClick={onClick}><span className={`policy-type-icon ${policy.policyType.toLowerCase()}`}><ShieldCheck size={18} /></span><span className="policy-mini-main"><strong>{policy.policyName}</strong><small>{policy.policyNumber} <span>·</span> {policy.policyType}</small><span className="mini-progress"><i style={{ width: `${Math.min(100, Math.max(3, Number(policy.totalPremiumPaid) / Number(policy.totalPremiumPayable) * 100))}%` }} /></span></span><span className="policy-mini-right"><strong>{money(policy.sumInsured)}</strong><small>sum insured</small></span><ChevronRight className="mini-chevron" size={17} /></button>
}
function ProductCard({ product, recommendation, onRegister }) {
  const typeIcon = product.policyType === 'HEALTH' ? <HeartPulse size={19} /> : product.policyType === 'SAVINGS' ? <ArrowUpRight size={19} /> : <ShieldCheck size={19} />
  return <article className="product-card"><div className="product-card-top"><span className={`product-symbol ${product.policyType.toLowerCase()}`}>{typeIcon}</span><span className="product-code">{product.productCode}</span>{recommendation && <span className={`suitability-tag suitability-${recommendation.suitability.toLowerCase()}`}>{recommendation.suitability.toLowerCase()}</span>}</div><div className="product-type">{product.policyType}</div><h3>{product.policyName}</h3><p className="product-description">{recommendation?.reason || product.description}</p><div className="product-cover"><span>Sum insured</span><strong>{money(product.sumInsured)}</strong></div><div className="product-meta"><div><small>Premium</small><strong>{money(product.premiumAmount)}<i> / {frequency(product.premiumFrequency).toLowerCase()}</i></strong></div><div><small>Policy term</small><strong>{product.termMonths >= 12 ? `${product.termMonths / 12} yr` : `${product.termMonths} mo`}</strong></div></div><button className="button button-outline full-button" onClick={onRegister}>View & register <ArrowRight size={15} /></button></article>
}
function QuickAction({ icon: Icon, title, text, action, onClick }) {
  return <button className="quick-action" onClick={onClick}><span className="quick-icon"><Icon size={18} /></span><span className="quick-copy"><strong>{title}</strong><small>{text}</small><em>{action} <ArrowRight size={13} /></em></span><ChevronRight className="quick-chevron" size={17} /></button>
}
function EmptyState({ icon: Icon, title, description, action, onAction }) {
  return <div className="empty-state"><span className="empty-icon"><Icon size={20} /></span><h3>{title}</h3><p>{description}</p>{action && <button className="button button-outline" onClick={onAction}>{action} <ArrowRight size={14} /></button>}</div>
}
function SummaryTile({ label, value, highlight }) { return <div className={`summary-tile ${highlight ? 'summary-highlight' : ''}`}><span>{label}</span><strong>{value}</strong></div> }

export default App
