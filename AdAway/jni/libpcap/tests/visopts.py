#!/usr/bin/env python

"""
This program parse the output from pcap_compile() to visualize the CFG after
each optimize phase.

Usage guide:
1. Enable optimizier debugging code when configure libpcap,
   and build libpcap & filtertest
       ./configure --enable-optimizer-dbg
       make
       make filtertest
2. Run filtertest to compile BPF expression, save to output a.txt
       ./filtertest EN10MB host 192.168.1.1 > a.txt
3. Send a.txt to this program's standard input
       cat a.txt | tests/visopts.py
4. Step 2&3 can be merged:
       ./filtertest EN10MB host 192.168.1.1 | tests/visopts.py
5. The standard output is something like this:
       generated files under directory: /tmp/visopts-W9ekBw
         the directory will be removed when this programs finished.
       open this link: http://localhost:39062/expr1.html
6. Using open link at the 3rd line `http://localhost:39062/expr1.html'

Note:
1. CFG graph is translated to SVG document, expr1.html embeded them as external
   document. If you open expr1.html as local file using file:// protocol, some
   browsers will deny such requests so the web pages will not shown properly.
   For chrome, you can run it using following command to avoid this:
       chromium --disable-web-security
   That's why this program start a localhost http server.
2. expr1.html use jquery from http://ajax.googleapis.com, so you need internet
   access to show the web page.
"""

import sys, os
import string
import subprocess
import json

html_template = string.Template("""
<html>
  <head>
    <title>BPF compiler optimization phases for $expr </title>
    <style type="text/css">
      .hc {
         /* half width container */
         display: inline-block;
         float: left;
         width: 50%;
      }
    </style>

    <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"/></script>
    <!--script type="text/javascript" src="./jquery.min.js"/></script-->
    <script type="text/javascript">
      var expr = '$expr';
      var exprid = 1;
      var gcount = $gcount;
      var logs = JSON.parse('$logs');
      logs[gcount] = "";

      var leftsvg = null;
      var rightsvg = null;

      function gurl(index) {
         index += 1;
         if (index < 10)
           s = "00" + index;
         else if (index < 100)
           s = "0" + index;
         else
           s = "" + index;
         return "./expr" + exprid + "_g" + s + ".svg"
      }

      function annotate_svgs() {
         if (!leftsvg || !rightsvg) return;

         $$.each([$$(leftsvg), $$(rightsvg)], function() {
           $$(this).find("[id|='block'][opacity]").each(function() {
             $$(this).removeAttr('opacity');
            });
          });

         $$(leftsvg).find("[id|='block']").each(function() {
           var has = $$(rightsvg).find("#" + this.id).length != 0;
           if (!has) $$(this).attr("opacity", "0.4");
           else {
             $$(this).click(function() {
                var target = $$(rightsvg).find("#" + this.id);
                var offset = $$("#rightsvgc").offset().top + target.position().top;
                window.scrollTo(0, offset);
                target.focus();
             });
           }
          });
         $$(rightsvg).find("[id|='block']").each(function() {
           var has = $$(leftsvg).find("#" + this.id).length != 0;
           if (!has) $$(this).attr("opacity", "0.4");
           else {
             $$(this).click(function() {
                var target = $$(leftsvg).find("#" + this.id);
                var offset = $$("#leftsvgc").offset().top + target.position().top;
                window.scrollTo(0, offset);
                target.focus();
             });
           }
          });
      }

      function init_svgroot(svg) {
         svg.setAttribute("width", "100%");
         svg.setAttribute("height", "100%");
      }
      function wait_leftsvg() {
         if (leftsvg) return;
         var doc = document.getElementById("leftsvgc").getSVGDocument();
         if (doc == null) {
            setTimeout(wait_leftsvg, 500);
            return;
         }
         leftsvg = doc.documentElement;
         //console.log(leftsvg);
         // initialize it
         init_svgroot(leftsvg);
         annotate_svgs();
      }
      function wait_rightsvg() {
         if (rightsvg) return;
         var doc = document.getElementById("rightsvgc").getSVGDocument();
         if (doc == null) {
            setTimeout(wait_rightsvg, 500);
            return;
         }
         rightsvg = doc.documentElement;
         //console.log(rightsvg);
         // initialize it
         init_svgroot(rightsvg);
         annotate_svgs();
      }
      function load_left(index) {
        var url = gurl(index);
        var frag = "<embed id='leftsvgc'  type='image/svg+xml' pluginspage='http://www.adobe.com/svg/viewer/install/' src='" + url + "'/>";
        $$("#lsvg").html(frag);
        $$("#lcomment").html(logs[index]);
        $$("#lsvglink").attr("href", url);
        leftsvg = null;
        wait_leftsvg();
      }
      function load_right(index) {
        var url = gurl(index);
        var frag = "<embed id='rightsvgc' type='image/svg+xml' pluginspage='http://www.adobe.com/svg/viewer/install/' src='" + url + "'/>";
        $$("#rsvg").html(frag);
        $$("#rcomment").html(logs[index]);
        $$("#rsvglink").attr("href", url);
        rightsvg = null;
        wait_rightsvg();
      }

      $$(document).ready(function() {
        for (var i = 0; i < gcount; i++) {
          var opt = "<option value='" + i + "'>loop" + i + " -- " + logs[i] + "</option>";
          $$("#lselect").append(opt);
          $$("#rselect").append(opt);
        }
        var on_selected = function() {
          var index = parseInt($$(this).children("option:selected").val());
          if (this.id == "lselect")
             load_left(index);
          else
             load_right(index);
        }
        $$("#lselect").change(on_selected);
        $$("#rselect").change(on_selected);

        $$("#backward").click(function() {
          var index = parseInt($$("#lselect option:selected").val());
          if (index <= 0) return;
          $$("#lselect").val(index - 1).change();
          $$("#rselect").val(index).change();
        });
        $$("#forward").click(function() {
          var index = parseInt($$("#rselect option:selected").val());
          if (index >= gcount - 1) return;
          $$("#lselect").val(index).change();
          $$("#rselect").val(index + 1).change();
        });

        if (gcount >= 1) $$("#lselect").val(0).change();
        if (gcount >= 2) $$("#rselect").val(1).change();
      });
    </script>
  </head>
  <body style="width: 96%">
    <div>
      <h1>$expr</h1>
      <div style="text-align: center;">
        <button id="backward" type="button">&lt;&lt;</button>
          &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
        <button id="forward" type="button">&gt;&gt;</button>
      </div>
    </div>
    <br/>
    <div style="clear: both;">
       <div class="hc lc">
        <select id="lselect"></select>
        <a id="lsvglink" target="_blank">open this svg in browser</a>
        <p id="lcomment"></p>
       </div>
       <div class="hc rc">
        <select id="rselect"></select>
        <a id="rsvglink" target="_blank">open this svg in browser</a>
        <p id="rcomment"></p>
       </div>
    </div>
    <br/>
    <div style="clear: both;">
       <div id="lsvg"  class="hc lc"></div>
       <div id="rsvg" class="hc rc"></div>
    </div>
  </body>
</html>
""")

def write_html(expr, gcount, logs):
    logs = map(lambda s: s.strip().replace("\n", "<br/>"), logs)

    global html_template
    html = html_template.safe_substitute(expr=expr.encode("string-escape"), gcount=gcount, logs=json.dumps(logs).encode("string-escape"))
    with file("expr1.html", "wt") as f:
        f.write(html)

def render_on_html(infile):
    expr = None
    gid = 1
    log = ""
    dot = ""
    indot = 0
    logs = []

    for line in infile:
        if line.startswith("machine codes for filter:"):
            expr = line[len("machine codes for filter:"):].strip()
            break
        elif line.startswith("digraph BPF {"):
            indot = 1
            dot = line
        elif indot:
            dot += line
            if line.startswith("}"):
                indot = 2
        else:
            log += line

        if indot == 2:
            p = subprocess.Popen(['dot', '-Tsvg'], stdin=subprocess.PIPE, stdout=subprocess.PIPE)
            svg = p.communicate(dot)[0]
            with file("expr1_g%03d.svg" % (gid), "wt") as f:
                f.write(svg)

            logs.append(log)
            gid += 1
            log = ""
            dot = ""
            indot = 0

    if indot != 0:
        #unterminated dot graph for expression
        return False
    if expr is None:
        # BPF parser encounter error(s)
        return False
    write_html(expr, gid - 1, logs)
    return True

def run_httpd():
    import SimpleHTTPServer
    import SocketServer

    class MySocketServer(SocketServer.TCPServer):
        allow_reuse_address = True
    Handler = SimpleHTTPServer.SimpleHTTPRequestHandler
    httpd = MySocketServer(("localhost", 0), Handler)
    print "open this link: http://localhost:%d/expr1.html" % (httpd.server_address[1])
    try:
        httpd.serve_forever()
    except KeyboardInterrupt as e:
        pass

def main():
    import tempfile
    import atexit
    import shutil
    os.chdir(tempfile.mkdtemp(prefix="visopts-"))
    atexit.register(shutil.rmtree, os.getcwd())
    print "generated files under directory: %s" % os.getcwd()
    print "  the directory will be removed when this programs finished."

    if not render_on_html(sys.stdin):
        return 1
    run_httpd()
    return 0

if __name__ == "__main__":
    if '-h' in sys.argv or '--help' in sys.argv:
        print __doc__
        exit(0)
    exit(main())
