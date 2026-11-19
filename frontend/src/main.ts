import { bootstrap, defaultDeps } from './bootstrap';

export { bootstrap } from './bootstrap';

const root = document.getElementById('app');
if (root !== null) {
  void bootstrap(defaultDeps(root));
}
