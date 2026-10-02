import type { FileDiff } from '../domain/timeline'

export function DiffPanel({ files }: { files: FileDiff[] }) {
  if (files.length === 0) {
    return <p className="empty panel">Os arquivos alterados pelo agente aparecem aqui.</p>
  }

  return (
    <div className="diff panel">
      {files.map((file) => (
        <section key={file.path} className="diff-file">
          <header>
            <code>{file.path}</code>
            <span className="additions">+{file.additions}</span>
            <span className="deletions">−{file.deletions}</span>
          </header>
          <pre>
            {file.patch
              .split('\n')
              .filter((line) => !line.startsWith('Index:') && !line.startsWith('====='))
              .map((line, index) => (
                <span key={index} className={lineClass(line)}>
                  {line + '\n'}
                </span>
              ))}
          </pre>
        </section>
      ))}
    </div>
  )
}

export function lineClass(line: string): string {
  if (line.startsWith('+++') || line.startsWith('---')) {
    return 'meta'
  }

  if (line.startsWith('@@')) {
    return 'hunk'
  }

  if (line.startsWith('+')) {
    return 'added'
  }

  return line.startsWith('-') ? 'removed' : ''
}
