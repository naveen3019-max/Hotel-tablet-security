import os

def replace_icons(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        
    # Remove import
    content = content.replace('import { TrashIcon, CheckIcon, WifiOffIcon } from "lucide-react";\n', '')
    
    # Add SVG components after formatting function
    icons = """
const TrashIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M3 6h18"/>
    <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
    <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
  </svg>
);

const CheckIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);
"""
    if "const TrashIcon" not in content:
        content = content.replace('const DASHBOARD_VERSION = "v4.0-redesign";\n', 'const DASHBOARD_VERSION = "v4.0-redesign";\n' + icons)
        
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

replace_icons('dashboard/src/app/page.tsx')
replace_icons('dashboard/src/app/enhanced-page.tsx')

print("Icons replaced with inline SVGs.")
