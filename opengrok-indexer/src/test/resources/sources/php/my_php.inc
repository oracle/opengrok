<?php
/**
 *
 * @file
 * @brief
 *
 * @responsible
 * @author
 * @version
 * @copyright
 */

class ConnectivityAssistant extends ManagerRpc {
	public $p_result = '';
	protected $p_parameters = array(
				'set' => array(
					'config'  => 'array',
					'revertTimeout' => 'integer'
				)
	);

  protected $p_manager = 'myproduct::ConnectivityAssistant';

	public function set($p_config, $p_revertTimeout) {
		return $this->p_handleResponse(&$p_errors, $this->p_call()->set(&$p_result, $p_config, $p_revertTimeout), $p_result);
	}
}; // Configuration
?>
