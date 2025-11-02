import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, useRef, useEffect } from 'react'
import { useDropzone } from 'react-dropzone'
import { Brain, Eye, X } from 'lucide-react'
import ReactMarkdown from 'react-markdown'
import gfm from 'remark-gfm'
import MdEditor from 'react-markdown-editor-lite'
import 'react-markdown-editor-lite/lib/index.css'

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

type QuickMode = 'text' | 'voice'

function GlobalDropOverlay({ onFiles }: { onFiles: (files: File[]) => void }) {
  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    noClick: true, noKeyboard: true,
    onDrop: accepted => accepted?.length && onFiles(accepted),
  });
  return (
    <div
      {...getRootProps()}
      className={`fixed inset-0 z-40 transition-all ${
        isDragActive ? 'pointer-events-auto bg-blue-500/10 border-2 border-dashed border-blue-400' : 'pointer-events-none'
      }`}
      aria-hidden={!isDragActive}
    >
      <input {...getInputProps()} />
    </div>
  );
}

function FloatingUploadTarget({ onFiles }: { onFiles: (files: File[]) => void }) {
  // Draggable position persisted in localStorage
  const [pos, setPos] = useState<{ x: number; y: number }>({ x: 16, y: 100 })
  const draggingRef = useRef<{ startX: number; startY: number; startLeft: number; startTop: number } | null>(null)

  useEffect(() => {
    try {
      const saved = localStorage.getItem('uploadWidgetPos')
      if (saved) {
        const p = JSON.parse(saved)
        if (typeof p?.x === 'number' && typeof p?.y === 'number') setPos(p)
      } else {
        // Default: bottom-left with 16px margin, approximate tile height
        setPos({ x: 16, y: Math.max(16, window.innerHeight - 100) })
      }
    } catch {}
  }, [])

  useEffect(() => {
    try { localStorage.setItem('uploadWidgetPos', JSON.stringify(pos)) } catch {}
  }, [pos])

  const onPointerDown = (e: React.PointerEvent) => {
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId)
    draggingRef.current = { startX: e.clientX, startY: e.clientY, startLeft: pos.x, startTop: pos.y }
  }
  const onPointerMove = (e: React.PointerEvent) => {
    if (!draggingRef.current) return
    const dx = e.clientX - draggingRef.current.startX
    const dy = e.clientY - draggingRef.current.startY
    const x = Math.max(8, Math.min(window.innerWidth - 140, draggingRef.current.startLeft + dx))
    const y = Math.max(8, Math.min(window.innerHeight - 60, draggingRef.current.startTop + dy))
    setPos({ x, y })
  }
  const onPointerUp = (e: React.PointerEvent) => {
    draggingRef.current = null
    ;(e.target as HTMLElement).releasePointerCapture?.(e.pointerId)
  }

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop: accepted => accepted?.length && onFiles(accepted),
  })

  return (
    <div className="fixed z-30" style={{ left: pos.x, top: pos.y }}>
      {/* Drag handle */}
      <div
        className="w-full px-2 py-1 text-[10px] text-gray-500 cursor-move select-none"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        title="Drag to move"
      >
        Drag
      </div>
      {/* Click/Drop area */}
      <div
        {...getRootProps()}
        className={`card cursor-pointer ${isDragActive ? 'ring-2 ring-blue-400' : ''}`}
        title="Click or drop a file to ingest"
      >
        <input {...getInputProps()} />
        <div className="text-sm text-gray-700">Click or drop to ingest</div>
      </div>
    </div>
  )
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
  const { data, isLoading, error } = useQuery({
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
        
        {error && (
          <div className="p-4 text-sm text-red-700 bg-red-50 border-b">{(error as any)?.message || 'Failed to load document.'}</div>
        )}
        {data && !error && (
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
                    <pre className="whitespace-pre-wrap text-sm text-gray-900 bg-gray-50 p-3 rounded border">
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

function RecentUploads({ onAskAbout }: { onAskAbout: (doc: { id: number, title: string }) => void }) {
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
                  <td className="py-2">
                    <span title={d.title} className="inline-block max-w-[320px] truncate align-bottom">
                      {d.title}
                    </span>
                  </td>
                  <td className="py-2">
                    <span title={d.contentType} className="inline-block max-w-[200px] truncate align-bottom">
                      {(d.contentType || '').split('/')[1] || d.contentType || 'unknown'}
                    </span>
                  </td>
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
                        onClick={() => onAskAbout({ id: d.id, title: d.title })}>
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
  const [quickOpen, setQuickOpen] = useState<QuickMode | null>(null)

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
          sourceFilter: sourceFilterRef.current,
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

  const sourceFilterRef = useRef<string[] | null>(null)
  const onAskAbout = (doc: { id: number, title: string }) => {
    const prompt = `Summarize the key points of "${doc.title}" and cite the most relevant sections.`
    setQuestion(prompt)
    sourceFilterRef.current = [`doc:${doc.id}`]
    ask.mutate()
    // clear after one turn to avoid sticky filter
    setTimeout(() => { sourceFilterRef.current = null }, 0)
  }

  // Shared uploader used by global drop overlay and floating target.
  const handleFiles = async (files: File[]) => {
    if (!files?.length) return;
    const file = files[0];
    const form = new FormData();
    form.append('file', file);
    setToast('Uploading…');
    try {
      const res = await fetch(`${API_BASE}/v1/ingest/upload`, { method: 'POST', body: form });
      const t = await res.text();
      if (!res.ok) throw new Error(t);
      setToast('File ingested successfully');
      await qc.invalidateQueries({ queryKey: ['recent'] });
    } catch (e: any) {
      setToast(`Upload failed: ${e?.message ?? e}`);
    }
  };

  return (
    <div>
      <header className="border-b bg-white">
        <div className="container flex items-center justify-between h-14">
          <div className="font-semibold">🧠 Fitsum Cortex</div>
        </div>
      </header>
      {/* Quick Add card */}
      <div className="container mt-4">
        <div className="mx-auto w-full">
          <div className="flex items-center gap-4">
            <button className="text-sm text-blue-600 hover:underline" onClick={() => setQuickOpen('text')}>Add text note</button>
            <button className="text-sm text-blue-600 hover:underline" onClick={() => setQuickOpen('voice')}>Add voice note</button>
          </div>
        </div>
      </div>
      {/* Full-width container with small gutters that adapts to screen size */}
      <main className="container mt-6">
        <div className="mx-auto w-full">
          {/* Global drop overlay (drop anywhere) and floating bottom-left target */}
          <GlobalDropOverlay onFiles={handleFiles} />
          <FloatingUploadTarget onFiles={handleFiles} />
          <div className="grid grid-cols-1 gap-6">
            <section className="space-y-3">
              <div className="card">
                <textarea
                  className="input min-h-[100px]"
                  placeholder="Ask a question about your knowledge base..."
                  value={question}
                  onChange={e => setQuestion(e.target.value)}
                  onKeyDown={e => {
                    if (loading) { e.preventDefault(); return; }
                    if (e.key === 'Enter' && !e.ctrlKey && !e.metaKey && !e.shiftKey && !e.altKey) {
                      e.preventDefault();
                      if (question.trim()) ask.mutate();
                    }
                    // For Ctrl+Enter (or Cmd+Enter), do nothing so the browser inserts a newline by default
                  }}
                />
                <div className="mt-2 flex items-center gap-3">
                  <button className="btn" onClick={() => ask.mutate()} disabled={loading || !question.trim()}>Ask</button>
                  {/^https?:\/\/\S+$/i.test(question.trim()) && (
                    <button className="text-sm text-blue-600" onClick={async () => {
                      const url = question.trim();
                      setToast('Fetching URL…')
                      try {
                        const res = await fetch(`${API_BASE}/v1/ingest/url`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ url }) })
                        const t = await res.text();
                        if (!res.ok) throw new Error(t)
                        setToast('URL ingested successfully')
                        await qc.invalidateQueries({ queryKey: ['recent'] })
                      } catch (e:any) {
                        setToast(`URL ingest failed: ${e?.message ?? e}`)
                      }
                    }}>Ingest URL</button>
                  )}
                  <label className="flex items-center gap-2 text-sm text-gray-600">
                    <input type="checkbox" checked={allowFallback} onChange={e => setAllowFallback(e.target.checked)} />
                    Allow general knowledge fallback
                  </label>
                  <div className="ml-auto text-xs text-gray-500">Press Enter to send • Ctrl+Enter for newline</div>
                </div>
              </div>
              <div className="card space-y-2">
                <div className="prose md:prose-lg max-w-none">
                  <ReactMarkdown remarkPlugins={[gfm]}>
                    {answer}
                  </ReactMarkdown>
                </div>
                {confidence != null && confidenceLabel && (
                  <div className="text-sm text-gray-600">
                    Confidence: <span className="font-medium">{confidenceLabel}</span> ({confidence.toFixed(2)})
                  </div>
                )}
              </div>

              {/* Recent uploads as collapsible below answer */}
              <details className="card" open>
                <summary className="cursor-pointer select-none text-sm text-gray-700">Recent uploads</summary>
                <div className="mt-3">
                  <RecentUploads onAskAbout={onAskAbout} />
                </div>
              </details>

              {/* Watcher status */}
              <WatcherStatusCard />
            </section>
          </div>
        </div>
      </main>
      {quickOpen && (
        <QuickOverlay mode={quickOpen} onClose={() => setQuickOpen(null)} onSaved={onUploaded} />
      )}
      {loading && (
        <div className="fixed inset-0 z-30 bg-black/20 flex items-start justify-center pt-24" aria-busy="true" aria-label="AI processing">
          <div className="bg-white border border-gray-200 rounded-md shadow px-3 py-2 text-sm flex items-center gap-2">
            <svg className="animate-spin h-4 w-4 text-blue-600" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"></path>
            </svg>
            <span>Thinking…</span>
          </div>
        </div>
      )}
      {toast && (
        <div className="fixed right-4 bottom-4 card shadow-lg">{toast}</div>
      )}
    </div>
  )
}

function WatcherStatusCard() {
  const qc = useQueryClient()
  const { data, isLoading, refetch } = useQuery({
    queryKey: ['watcher-status'],
    queryFn: async () => {
      const res = await fetch(`${API_BASE}/v1/watcher/status`)
      if (!res.ok) throw new Error('Watcher unavailable')
      return res.json() as Promise<{
        enabled: boolean
        root: string
        processedRoot: string
        recursive: boolean
        pollInterval: string
        lastScanStart: number
        lastScanEnd: number
        scanned: number
        ingested: number
        failed: number
      }>
    }
  })

  const scan = async () => {
    await fetch(`${API_BASE}/v1/watcher/scan`, { method: 'POST' })
    await qc.invalidateQueries({ queryKey: ['watcher-status'] })
  }

  return (
    <div className="card">
      <div className="flex items-center justify-between mb-2">
        <h3 className="font-semibold">Folder watcher</h3>
        <div className="flex items-center gap-2">
          <button className="text-sm text-blue-600" onClick={() => refetch()} disabled={isLoading}>Refresh</button>
          <button className="text-sm text-blue-600" onClick={scan} disabled={isLoading}>Scan now</button>
        </div>
      </div>
      {isLoading && <div className="text-sm text-gray-600">Loading…</div>}
      {data && (
        <div className="text-sm text-gray-700 space-y-1">
          <div><span className="text-gray-500">Enabled:</span> {String(data.enabled)}</div>
          <div><span className="text-gray-500">Root:</span> {data.root || '—'}</div>
          <div><span className="text-gray-500">Processed:</span> {data.processedRoot || '—'}</div>
          <div><span className="text-gray-500">Interval:</span> {data.pollInterval || '—'}</div>
          <div className="mt-2">
            <span className="text-gray-500">Last scan:</span> {data.lastScanEnd ? new Date(data.lastScanEnd).toLocaleString() : '—'}
          </div>
          <div className="flex gap-4">
            <div>Scanned: {data.scanned}</div>
            <div>Ingested: {data.ingested}</div>
            <div>Failed: {data.failed}</div>
          </div>
        </div>
      )}
    </div>
  )
}

function QuickAdd({ onSaved }: { onSaved: () => void }) {
  const [tab, setTab] = useState<'text' | 'voice'>('text')
  const [title, setTitle] = useState('')
  const [tags, setTags] = useState('')
  const [text, setText] = useState('')
  const [recording, setRecording] = useState(false)
  const [status, setStatus] = useState<string | null>(null)

  // Voice transcription via Web Speech API (if available)
  useEffect(() => {
    if (tab !== 'voice') return
    let recognition: any
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
      const SR: any = (window as any).webkitSpeechRecognition || (window as any).SpeechRecognition
      recognition = new SR()
      recognition.continuous = true
      recognition.interimResults = true
      recognition.lang = 'en-US'
      // Append only FINAL results to main text; keep interim preview ephemeral
      recognition.onresult = (event: any) => {
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          const res = event.results[i]
          const phrase = (res[0]?.transcript || '').trim()
          if (!phrase) continue
          if (res.isFinal) {
            setText(prev => dedupAppend(prev, phrase))
          } else {
            // optional: could set a live preview state here if desired
          }
        }
      }
      if (recording) recognition.start()
      return () => { try { recognition && recognition.stop() } catch {} }
    } else {
      setStatus('Voice input not supported in this browser. Use Text tab or native dictation.')
    }
  }, [tab, recording])

  const save = async () => {
    const body = { title: title.trim() || undefined, content: text, tags: tags.split(',').map(s => s.trim()).filter(Boolean) }
    if (!body.content || !body.content.trim()) { setStatus('Please enter some text.'); return }
    setStatus('Saving…')
    const res = await fetch(`${API_BASE}/v1/ingest/text`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    const t = await res.text()
    if (!res.ok) { setStatus(`Failed: ${t}`); return }
    setStatus('Saved')
    setTitle(''); setTags(''); setText('')
    onSaved()
    setTimeout(() => setStatus(null), 1500)
  }

  return (
    <div>
      <div className="flex items-center gap-4 mb-3">
        <button className={`text-sm ${tab==='text'?'font-semibold text-blue-600':'text-gray-600'}`} onClick={() => setTab('text')}>Text</button>
        <button className={`text-sm ${tab==='voice'?'font-semibold text-blue-600':'text-gray-600'}`} onClick={() => setTab('voice')}>Voice</button>
      </div>
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
        <input className="input" placeholder="Title (optional)" value={title} onChange={e => setTitle(e.target.value)} />
        <input className="input" placeholder="Tags comma-separated (optional)" value={tags} onChange={e => setTags(e.target.value)} />
        <div className="flex items-center gap-3">
          <button className="btn" onClick={save}>Save to knowledge base</button>
          {status && <span className="text-sm text-gray-600">{status}</span>}
        </div>
      </div>
      <div className="mt-3">
        {tab === 'text' ? (
          <textarea className="input min-h-[120px]" placeholder="Paste or type notes…" value={text} onChange={e => setText(e.target.value)} />
        ) : (
          <div className="flex items-center gap-3">
            <button className="btn" onClick={() => setRecording(r => !r)}>{recording ? 'Stop' : 'Record'}</button>
            <span className="text-sm text-gray-600">{recording ? 'Listening…' : 'Use browser/OS dictation if supported'}</span>
          </div>
        )}
      </div>
    </div>
  )
}

function QuickOverlay({ mode, onClose, onSaved }: { mode: QuickMode, onClose: () => void, onSaved: () => void }) {
  const [title, setTitle] = useState('')
  const [tags, setTags] = useState('')
  const [text, setText] = useState('')
  const [recording, setRecording] = useState(false)
  const [status, setStatus] = useState<string | null>(null)

  useEffect(() => {
    if (mode !== 'voice') return
    let recognition: any
    if ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window) {
      const SR: any = (window as any).webkitSpeechRecognition || (window as any).SpeechRecognition
      recognition = new SR()
      recognition.continuous = true
      recognition.interimResults = true
      recognition.lang = 'en-US'
      recognition.onresult = (event: any) => {
        for (let i = event.resultIndex; i < event.results.length; ++i) {
          const res = event.results[i]
          const phrase = (res[0]?.transcript || '').trim()
          if (!phrase) continue
          if (res.isFinal) setText(prev => dedupAppend(prev, phrase))
        }
      }
      if (recording) recognition.start()
      return () => { try { recognition && recognition.stop() } catch {} }
    } else {
      setStatus('Voice input not supported in this browser. Use Text note instead.')
    }
  }, [mode, recording])

  const save = async () => {
    const body = { title: title.trim() || undefined, content: text, tags: tags.split(',').map(s => s.trim()).filter(Boolean) }
    if (!body.content || !body.content.trim()) { setStatus('Please enter some text.'); return }
    setStatus('Saving…')
    const res = await fetch(`${API_BASE}/v1/ingest/text`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
    const t = await res.text()
    if (!res.ok) { setStatus(`Failed: ${t}`); return }
    setStatus('Saved')
    setTitle(''); setTags(''); setText('')
    onSaved(); onClose()
  }

  return (
    <div className="overlay-backdrop" role="dialog" aria-modal="true">
      <div className="overlay-card">
        <div className="p-4 border-b flex items-center justify-between">
          <div className="font-semibold">{mode === 'text' ? 'Add text note' : 'Add voice note'}</div>
          <button className="text-gray-500 hover:text-gray-700" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="p-4 space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <input className="input" placeholder="Title (optional)" value={title} onChange={e => setTitle(e.target.value)} />
            <input className="input" placeholder="Tags comma-separated (optional)" value={tags} onChange={e => setTags(e.target.value)} />
            <div className="flex items-center gap-3">
              <button className="btn" onClick={save}>Save to knowledge base</button>
              {status && <span className="text-sm text-gray-600">{status}</span>}
            </div>
          </div>
          {mode === 'text' ? (
            <div className="min-h-[300px]">
              <MdEditor
                value={text}
                style={{ height: '300px' }}
                renderHTML={md => md}
                onChange={({ text }) => setText(text)}
              />
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-3">
                <button className="btn" onClick={() => setRecording(r => !r)}>{recording ? 'Stop' : 'Record'}</button>
                <span className="text-sm text-gray-600">{recording ? 'Listening…' : 'Press to start recording'}</span>
              </div>
              <div>
                <label className="text-sm text-gray-600">Transcript (editable):</label>
                <MdEditor
                  value={text}
                  style={{ height: '300px' }}
                  renderHTML={md => md}
                  onChange={({ text }) => setText(text)}
                />
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
// Prevent duplicate progressive prefixes when appending dictation
function dedupAppend(existing: string, phrase: string): string {
  const prev = existing.trim()
  if (!prev) return phrase
  // If phrase starts with the last up-to 50 chars of prev, remove that overlap
  const tail = prev.slice(Math.max(0, prev.length - 50))
  if (phrase.startsWith(tail)) {
    return (prev + ' ' + phrase.slice(tail.length)).trim()
  }
  // Collapse repeated word at boundary (e.g., "...today" + "today ...")
  const lastWordMatch = prev.match(/(\b\w+)$'/)
  const lastWord = lastWordMatch ? lastWordMatch[1] : ''
  if (lastWord && phrase.toLowerCase().startsWith(lastWord.toLowerCase() + ' ')) {
    return (prev + ' ' + phrase.slice(lastWord.length + 1)).trim()
  }
  return (prev + ' ' + phrase).trim()
}


