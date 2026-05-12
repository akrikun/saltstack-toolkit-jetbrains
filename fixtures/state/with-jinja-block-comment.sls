# YAML line comment (handled by `#`)

{# Jinja block comment — the SlsCommenter exposes `{#` / `#}`
   so Cmd+Shift+/ wraps a selection in this form. The block can
   span multiple lines and is the only block-comment form Salt
   accepts in an .sls file (since the file is YAML+Jinja). #}

nginx_pkg:
  pkg.installed:
    - name: nginx

{# also valid: single-line block comment #}
nginx_service:
  service.running:
    - name: nginx
    - enable: True
