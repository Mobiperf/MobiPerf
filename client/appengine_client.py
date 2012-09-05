# Copyright 2012 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#!/usr/bin/python2.4
#

"""Python routines for authenticating against AppEngine.

This code is modified from the Rietveld upload.py code found here:

  http://codereview.appspot.com/static/upload.py

"""

__author__ = ['mdw@google.com (Matt Welsh)',
              'royman@google.com (Roy McElmurry)']

import cookielib
import logging
import socket
import sys
import urllib
import urllib2
import urlparse


AUTH_ACCOUNT_TYPE = 'GOOGLE'


class ClientLoginError(urllib2.HTTPError):
  """Raised to indicate there was an error authenticating with ClientLogin."""

  def __init__(self, url, code, msg, headers, args):
    urllib2.HTTPError.__init__(self, url, code, msg, headers, None)
    self.args = args
    self.reason = args['Error']
    self.info = args.get('Info', None)


class AbstractRpcServer(object):
  """Provides a common interface for a simple RPC server."""

  def __init__(self, host, auth_function, host_override=None,
               extra_headers={}, account_type=AUTH_ACCOUNT_TYPE):
    """Creates a new HttpRpcServer.

    Args:
      host: The host to send requests to.
      auth_function: A function that takes no arguments and returns an
        (email, password) tuple when called. Will be called if authentication
        is required.
      host_override: The host header to send to the server (defaults to host).
      extra_headers: A dict of extra headers to append to every request.
      account_type: Account type used for authentication. Defaults to
        AUTH_ACCOUNT_TYPE.
    """
    self.host = host
    if (not self.host.startswith('http://') and
        not self.host.startswith('https://')):
      self.host = 'http://' + self.host
    self.host_override = host_override
    self.auth_function = auth_function
    self.authenticated = False
    self.extra_headers = extra_headers
    self.account_type = account_type
    self.opener = self._GetOpener()
    if self.host_override:
      logging.info('Server: %s; Host: %s', self.host, self.host_override)
    else:
      logging.info('Server: %s', self.host)

  def _GetOpener(self):
    """Returns an OpenerDirector for making HTTP requests.

    Returns:
      A urllib2.OpenerDirector object.
    """
    raise NotImplementedError()

  def _CreateRequest(self, url, data=None):
    """Creates a new urllib request."""
    logging.debug('Creating request for: "%s" with payload:\n%s', url, data)
    req = urllib2.Request(url, data=data)
    if self.host_override:
      req.add_header('Host', self.host_override)
    for key, value in self.extra_headers.iteritems():
      req.add_header(key, value)
    return req

  def _GetAuthToken(self, email, password):
    """Uses ClientLogin to authenticate the user, returning an auth token.

    Args:
      email:    The user's email address
      password: The user's password

    Raises:
      ClientLoginError: If there was an error authenticating with ClientLogin.
      HTTPError: If there was some other form of HTTP error.

    Returns:
      The authentication token returned by ClientLogin.
    """
    account_type = self.account_type
    if self.host.endswith('.google.com'):
      # Needed for use inside Google.
      account_type = 'HOSTED'
    req = self._CreateRequest(
        url='https://www.google.com/accounts/ClientLogin',
        data=urllib.urlencode({
            'Email': email,
            'Passwd': password,
            'service': 'ah',
            'source': 'appengine-client',
            'accountType': account_type,
        }),
    )
    try:
      response = self.opener.open(req)
      response_body = response.read()
      response_dict = dict(x.split('=')
                           for x in response_body.split('\n') if x)
      return response_dict['Auth']
    except urllib2.HTTPError, e:
      if e.code == 403:
        body = e.read()
        response_dict = dict(x.split('=', 1) for x in body.split('\n') if x)
        raise ClientLoginError(req.get_full_url(), e.code, e.msg,
                               e.headers, response_dict)
      else:
        raise

  def _GetAuthCookie(self, auth_token):
    """Fetches authentication cookies for an authentication token.

    Args:
      auth_token: The authentication token returned by ClientLogin.

    Raises:
      HTTPError: If there was an error fetching the authentication cookies.
    """
    # This is a dummy value to allow us to identify when we're successful.
    continue_location = 'http://localhost/'
    args = {'continue': continue_location, 'auth': auth_token}
    req = self._CreateRequest('%s/_ah/login?%s' %
                              (self.host, urllib.urlencode(args)))
    try:
      response = self.opener.open(req)
    except urllib2.HTTPError, e:
      response = e
    if (response.code != 302 or
        response.info()['location'] != continue_location):
      raise urllib2.HTTPError(req.get_full_url(), response.code, response.msg,
                              response.headers, response.fp)
    self.authenticated = True

  def _Authenticate(self):
    """Authenticates the user.

    The authentication process works as follows:
     1) We get a username and password from the user
     2) We use ClientLogin to obtain an AUTH token for the user
        (see http://code.google.com/apis/accounts/AuthForInstalledApps.html).
     3) We pass the auth token to /_ah/login on the server to obtain an
        authentication cookie. If login was successful, it tries to redirect
        us to the URL we provided.

    If we attempt to access the upload API without first obtaining an
    authentication cookie, it returns a 401 response (or a 302) and
    directs us to authenticate ourselves with ClientLogin.
    """
    for i in range(3):
      credentials = self.auth_function()
      try:
        auth_token = self._GetAuthToken(credentials[0], credentials[1])
      except ClientLoginError, e:
        print >>sys.stderr, ''
        if e.reason == 'BadAuthentication':
          if e.info == 'InvalidSecondFactor':
            print >>sys.stderr, (
                'Use an application-specific password instead '
                'of your regular account password.\n'
                'See http://www.google.com/'
                'support/accounts/bin/answer.py?answer=185833')
          else:
            print >>sys.stderr, 'Invalid username or password.'
        elif e.reason == 'CaptchaRequired':
          print >>sys.stderr, (
              'Please go to\n'
              'https://www.google.com/accounts/DisplayUnlockCaptcha\n'
              'and verify you are a human.  Then try again.\n'
              'If you are using a Google Apps account the URL is:\n'
              'https://www.google.com/a/yourdomain.com/UnlockCaptcha')
        elif e.reason == 'NotVerified':
          print >>sys.stderr, 'Account not verified.'
        elif e.reason == 'TermsNotAgreed':
          print >>sys.stderr, 'User has not agreed to TOS.'
        elif e.reason == 'AccountDeleted':
          print >>sys.stderr, 'The user account has been deleted.'
        elif e.reason == 'AccountDisabled':
          print >>sys.stderr, 'The user account has been disabled.'
          break
        elif e.reason == 'ServiceDisabled':
          print >>sys.stderr, ('The user\'s access to the service has been '
                               'disabled.')
        elif e.reason == 'ServiceUnavailable':
          print >>sys.stderr, 'The service is not available; try again later.'
        else:
          # Unknown error.
          raise
        print >>sys.stderr, ''
        continue
      self._GetAuthCookie(auth_token)
      return

  def Send(self, request_path, payload=None,
           content_type='application/octet-stream',
           timeout=None,
           extra_headers=None,
           **kwargs):
    """Sends an RPC and returns the response.

    Args:
      request_path: The path to send the request to, eg /api/appversion/create.
      payload: The body of the request, or None to send an empty request.
      content_type: The Content-Type header to use.
      timeout: timeout in seconds; default None i.e. no timeout.
        (Note: for large requests on OS X, the timeout doesn't work right.)
      extra_headers: Dict containing additional HTTP headers that should be
        included in the request (string header names mapped to their values),
        or None to not include any additional headers.
      kwargs: Any keyword arguments are converted into query string parameters.

    Returns:
      The response body, as a string.
    """
    # TODO(mdw): Don't require authentication.  Let the server say
    # whether it is necessary.
    if not self.authenticated:
      self._Authenticate()

    old_timeout = socket.getdefaulttimeout()
    socket.setdefaulttimeout(timeout)
    try:
      tries = 0
      while True:
        tries += 1
        args = dict(kwargs)
        url = '%s%s' % (self.host, request_path)
        if args:
          url += '?' + urllib.urlencode(args)

        req = self._CreateRequest(url=url, data=payload)
        req.add_header('Content-Type', content_type)
        if extra_headers:
          for header, value in extra_headers.items():
            req.add_header(header, value)
        try:
          f = self.opener.open(req)
          response = f.read()
          f.close()
          return response
        except urllib2.HTTPError, e:
          if tries > 3:
            raise
          elif e.code == 401 or e.code == 302:
            self._Authenticate()
          elif e.code == 301:
            # Handle permanent redirect manually.
            url = e.info()['location']
            url_loc = urlparse.urlparse(url)
            self.host = '%s://%s' % (url_loc[0], url_loc[1])
          else:
            raise
    finally:
      socket.setdefaulttimeout(old_timeout)


class HttpRpcServer(AbstractRpcServer):
  """Provides a simplified RPC-style interface for HTTP requests."""

  def _GetOpener(self):
    """Returns an OpenerDirector that supports cookies and ignores redirects.

    Returns:
      A urllib2.OpenerDirector object.
    """
    opener = urllib2.OpenerDirector()
    opener.add_handler(urllib2.ProxyHandler())
    opener.add_handler(urllib2.UnknownHandler())
    opener.add_handler(urllib2.HTTPHandler())
    opener.add_handler(urllib2.HTTPDefaultErrorHandler())
    opener.add_handler(urllib2.HTTPSHandler())
    opener.add_handler(urllib2.HTTPErrorProcessor())
    self.cookie_jar = cookielib.CookieJar()
    opener.add_handler(urllib2.HTTPCookieProcessor(self.cookie_jar))
    return opener


class DevAppServerHttpRpcServer(HttpRpcServer):
  """Subclass of HttpRpcServer that talks to a dev_appserver instance."""

  def __init__(self, host, username='test@example.com', host_override=None,
               extra_headers={}, account_type=AUTH_ACCOUNT_TYPE):
    auth_function = lambda: (username, 'fakepassword')
    extra_headers = {'Cookie': 'dev_appserver_login="%s:False"' % username}
    super(HttpRpcServer, self).__init__(host, auth_function,
                                        host_override, extra_headers,
                                        account_type)
    self.authenticated = True
