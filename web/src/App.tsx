import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useRef, useEffect } from 'react'
import { useDropzone } from 'react-dropzone'
import { Brain } from 'lucide-react'

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

type AskResponse = {
  answer: string
  citations?: { documentTitle: string; location: string; snippet: string }[]
  confidence: number
  provider: string
  traceId: string
  latencyMs: number
  sensitivity: string
}

function UploadCard({ onUploaded }: { onUploaded: () => void }) {
  const [uploading, setUploading] = useState(false)
  const onDrop = (acceptedFiles: File[]) => {
    if (!acceptedFiles.length) return
    const file = acceptedFiles[0]
    const form = new FormData()
    form.append('file', file)
    setUploading(true)
    fetch(`${API_BASE}/v1/ingest/upload`, { method: 'POST', body: form })
      .then(async r => {
        const t = await r.text()
        if (!r.ok) throw new Error(t)
        return t
      })
      .then(() => onUploaded())
      .catch(err => onUploaded() || alert(`Upload failed: ${err.message ?? err}`))
      .finally(() => setUploading(false))
  }
  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop })
  return (
    <div className={`card ${uploading ? 'opacity-70 pointer-events-none' : ''}`} {...getRootProps()}>
      <input {...getInputProps()} />
      <p className="text-gray-600">{uploading ? 'Uploading…' : (isDragActive ? 'Drop the file here…' : 'Click or drop a file to ingest')}</p>
    </div>
  )
}

function RecentUploads({ onAskAbout }: { onAskAbout: (title: string) => void }) {
  const { data, refetch, isLoading } = useQuery({
    queryKey: ['recent'],
    queryFn: async () => (await fetch(`${API_BASE}/v1/ingest/recent?limit=10`)).json()
  })
  const del = async (id: number) => {
    if (!confirm('Delete this document?')) return
    const res = await fetch(`${API_BASE}/v1/ingest/document/${id}`, { method: 'DELETE' })
    if (!res.ok) alert(await res.text())
    else refetch()
  }
  return (
    <div className="card">
      <div className="flex items-center justify-between mb-2">
        <h3 className="font-semibold">Recent uploads</h3>
        <button className="text-sm text-blue-600" onClick={() => refetch()} disabled={isLoading}>Refresh</button>
      </div>
      <div className="overflow-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-gray-500">
              <th className="py-1">Title</th>
              <th className="py-1">Type</th>
              <th className="py-1">Indexed</th>
              <th className="py-1 text-right">Actions</th>
            </tr>
          </thead>
          <tbody>
            {Array.isArray(data) && data.length > 0 ? data.map((d: any) => (
              <tr key={d.id} className="border-t">
                <td className="py-2">{d.title}</td>
                <td className="py-2">{d.contentType}</td>
                <td className="py-2">{d.indexedAt?.replace('T',' ').substring(0,19)}</td>
                <td className="py-2">
                  <div className="flex justify-end">
                    <button className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-700"
                      title="Ask about this file"
                      onClick={() => onAskAbout(d.title)}>
                      <Brain size={16} /> Ask
                    </button>
                    <button className="ml-3 text-red-600 hover:text-red-700"
                      title="Delete"
                      onClick={() => del(d.id)}>Delete</button>
                  </div>
                </td>
              </tr>
            )) : (
              <tr><td className="py-2 text-gray-500" colSpan={4}>No documents yet.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default function App() {
  const [question, setQuestion] = useState('')
  const [allowFallback, setAllowFallback] = useState(false)
  const [answer, setAnswer] = useState('Waiting for input...')
  const [loading, setLoading] = useState(false)
  const [toast, setToast] = useState<string | null>(null)
  const sessionRef = useRef<string>(() => crypto.randomUUID() as any as string)
  const qc = useQueryClient()

  // auto-hide toast
  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 3000)
    return () => clearTimeout(t)
  }, [toast])

  const ask = useMutation({
    mutationFn: async () => {
      const res = await fetch(`${API_BASE}/v1/ask`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          question: question.trim(),
          sourceFilter: null,
          allowFallback,
          sessionId: sessionRef.current
        })
      })
      if (!res.ok) throw new Error(await res.text())
      return res.json() as Promise<AskResponse>
    },
    onMutate: () => { setLoading(true); setAnswer('...'); },
    onError: (e: any) => {
      setLoading(false)
      // Try to parse backend JSON error for a human-friendly message
      let msg = e?.message ?? 'Request failed'
      try {
        const j = JSON.parse(msg)
        if (j?.error) msg = j.error
      } catch {}
      setAnswer(`There was a problem generating an answer.
Possible causes: local model timeout or context size limits.
Details: ${msg}`)
    },
    onSuccess: (r) => {
      setLoading(false)
      setAnswer(r.answer)
    }
  })

  const onUploaded = async () => {
    setToast('File ingested successfully')
    await qc.invalidateQueries({ queryKey: ['recent'] })
  }

  const onAskAbout = (title: string) => {
    const prompt = `Summarize the key points of "${title}" and cite the most relevant sections.`
    setQuestion(prompt)
    ask.mutate()
  }

  return (
    <div>
      <header className="border-b bg-white">
        <div className="container flex items-center justify-between h-14">
          <div className="font-semibold">🧠 Fitsum Cortex</div>
        </div>
      </header>
      <main className="container grid grid-cols-1 lg:grid-cols-3 gap-6 mt-6">
        <section className="lg:col-span-2 space-y-3">
          <div className="card">
            <textarea className="input min-h-[80px]" placeholder="Ask a question about your knowledge base..."
              value={question} onChange={e => setQuestion(e.target.value)} />
            <div className="mt-2 flex items-center gap-3">
              <button className="btn" onClick={() => ask.mutate()} disabled={loading || !question.trim()}>Ask</button>
              <label className="flex items-center gap-2 text-sm text-gray-600">
                <input type="checkbox" checked={allowFallback} onChange={e => setAllowFallback(e.target.checked)} />
                Allow general knowledge fallback
              </label>
            </div>
          </div>
          <div className="card min-h-[220px]">
            <div className="prose max-w-none whitespace-pre-wrap">{answer}</div>
          </div>
        </section>
        <aside className="space-y-3">
          <UploadCard onUploaded={onUploaded} />
          <RecentUploads onAskAbout={onAskAbout} />
        </aside>
      </main>
      {toast && (
        <div className="fixed right-4 bottom-4 card shadow-lg">{toast}</div>
      )}
    </div>
  )
}


