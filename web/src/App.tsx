import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useRef, useEffect } from 'react'
import { useDropzone } from 'react-dropzone'
import { Brain, Eye, X } from 'lucide-react'

const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080'

type AskResponse = {
  answer: string
  citations?: { documentTitle: string; location: string; snippet: string }[]
  confidence: number
  confidenceLabel: string
  provider: string
  traceId: string
  latencyMs: number
  sensitivity: string
}

type Document = {
  id: number
  title: string
  contentType: string
  contentHash: string
  rawContent: string
  metadataJson: string
  indexedAt: string
  createdAt: string
}

type Chunk = {
  id: number
  documentId: number
  chunkIndex: number
  content: string
  contentHash: string
  tokenCount: number
  heading: string
  pageNumber: number
  createdAt: string
}

type DocumentWithChunks = {
  document: Document
  chunks: Chunk[]
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

function DocumentDetailModal({ documentId, onClose }: { documentId: number | null, onClose: () => void }) {
  const { data, isLoading } = useQuery({
    queryKey: ['document', documentId],
    queryFn: async () => {
      const res = await fetch(`${API_BASE}/v1/ingest/document/${documentId}`)
      if (!res.ok) throw new Error('Document not found')
      return res.json() as Promise<DocumentWithChunks>
    },
    enabled: !!documentId
  })

  const [activeTab, setActiveTab] = useState<'content' | 'chunks' | 'metadata'>('content')

  if (!documentId) return null

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
      <div className="bg-white rounded-lg shadow-xl max-w-4xl w-full max-h-[90vh] flex flex-col">
        <div className="flex items-center justify-between p-4 border-b">
          <h2 className="text-lg font-semibold">
            {isLoading ? 'Loading...' : data?.document.title}
          </h2>
          <button onClick={onClose} className="text-gray-500 hover:text-gray-700">
            <X size={20} />
          </button>
        </div>
        
        {data && (
          <div className="flex-1 overflow-hidden">
            <div className="flex border-b">
              <button
                className={`px-4 py-2 text-sm ${activeTab === 'content' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-600'}`}
                onClick={() => setActiveTab('content')}
              >
                Content
              </button>
              <button
                className={`px-4 py-2 text-sm ${activeTab === 'chunks' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-600'}`}
                onClick={() => setActiveTab('chunks')}
              >
                Chunks ({data.chunks.length})
              </button>
              <button
                className={`px-4 py-2 text-sm ${activeTab === 'metadata' ? 'border-b-2 border-blue-500 text-blue-600' : 'text-gray-600'}`}
                onClick={() => setActiveTab('metadata')}
              >
                Metadata
              </button>
            </div>
            
            <div className="p-4 overflow-auto max-h-[60vh]">
              {activeTab === 'content' && (
                <div className="space-y-4">
                  <div className="text-sm text-gray-600">
                    <p><strong>Type:</strong> {data.document.contentType}</p>
                    <p><strong>Indexed:</strong> {new Date(data.document.indexedAt).toLocaleString()}</p>
                    <p><strong>Content Hash:</strong> {data.document.contentHash}</p>
                  </div>
                  <div className="prose max-w-none">
                    <pre className="whitespace-pre-wrap text-sm bg-gray-50 p-3 rounded border">
                      {data.document.rawContent || 'No content available'}
                    </pre>
                  </div>
                </div>
              )}
              
              {activeTab === 'chunks' && (
                <div className="space-y-3">
                  {data.chunks.map((chunk, index) => (
                    <div key={chunk.id} className="border rounded p-3">
                      <div className="flex justify-between items-start mb-2">
                        <div className="text-sm text-gray-600">
                          <span className="font-medium">Chunk {chunk.chunkIndex}</span>
                          {chunk.heading && <span> • {chunk.heading}</span>}
                          {chunk.pageNumber && <span> • Page {chunk.pageNumber}</span>}
                          <span> • {chunk.tokenCount} tokens</span>
                        </div>
                      </div>
                      <div className="text-sm bg-gray-50 p-2 rounded">
                        {chunk.content}
                      </div>
                    </div>
                  ))}
                </div>
              )}
              
              {activeTab === 'metadata' && (
                <div className="space-y-4">
                  <div className="text-sm text-gray-600">
                    <p><strong>Document ID:</strong> {data.document.id}</p>
                    <p><strong>Created:</strong> {new Date(data.document.createdAt).toLocaleString()}</p>
                    <p><strong>Content Type:</strong> {data.document.contentType}</p>
                    <p><strong>Content Hash:</strong> {data.document.contentHash}</p>
                  </div>
                  {data.document.metadataJson && (
                    <div>
                      <h4 className="font-medium mb-2">Raw Metadata:</h4>
                      <pre className="text-xs bg-gray-50 p-3 rounded border overflow-auto">
                        {JSON.stringify(JSON.parse(data.document.metadataJson), null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function RecentUploads({ onAskAbout }: { onAskAbout: (title: string) => void }) {
  const [viewingDocument, setViewingDocument] = useState<number | null>(null)
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
    <>
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
                    <div className="flex justify-end gap-2">
                      <button className="inline-flex items-center gap-1 text-blue-600 hover:text-blue-700"
                        title="View document details"
                        onClick={() => setViewingDocument(d.id)}>
                        <Eye size={16} /> View
                      </button>
                      <button className="inline-flex items-center gap-1 text-green-600 hover:text-green-700"
                        title="Ask about this file"
                        onClick={() => onAskAbout(d.title)}>
                        <Brain size={16} /> Ask
                      </button>
                      <button className="text-red-600 hover:text-red-700"
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
      <DocumentDetailModal 
        documentId={viewingDocument} 
        onClose={() => setViewingDocument(null)} 
      />
    </>
  )
}

export default function App() {
  const [question, setQuestion] = useState('')
  const [allowFallback, setAllowFallback] = useState(false)
  const [answer, setAnswer] = useState('Waiting for input...')
  const [confidence, setConfidence] = useState<number | null>(null)
  const [confidenceLabel, setConfidenceLabel] = useState<string | null>(null)
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
      setConfidence(r.confidence)
      setConfidenceLabel(r.confidenceLabel)
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
          <div className="card min-h-[220px] space-y-2">
            <div className="prose max-w-none whitespace-pre-wrap">{answer}</div>
            {confidence != null && confidenceLabel && (
              <div className="text-sm text-gray-600">
                Confidence: <span className="font-medium">{confidenceLabel}</span> ({confidence.toFixed(2)})
              </div>
            )}
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


