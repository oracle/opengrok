# MIT License
# 
# Copyright (c) 2016 Raimund Andée
# 
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
# 
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
# 
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

#region Internals
#region .net Types    
$certStoreTypes = @'
...
'@

$pkiInternalsTypes = @'
...
    /// <summary>
    /// 2.28 msPKI-Certificate-Name-Flag Attribute
    /// https://msdn.microsoft.com/en-us/library/cc226548.aspx
    /// </summary>
...
'@

$gpoType = @'
...
'@
#endregion .net Types

$ApplicationPolicies = @{
    # Remote Desktop
    'Remote Desktop' = '1.3.6.1.4.1.311.54.1.2'
    # Windows Update
    'Windows Update' = '1.3.6.1.4.1.311.76.6.1'
    # Windows Third Party Applicaiton Component
    'Windows Third Party Application Component' = '1.3.6.1.4.1.311.10.3.25'
    # Windows TCB Component
    'Windows TCB Component' = '1.3.6.1.4.1.311.10.3.23'
    # Windows Store
    'Windows Store' = '1.3.6.1.4.1.311.76.3.1'
    #...
}

$ExtendedKeyUsages = @{
    OldAuthorityKeyIdentifier = '.29.1'
    OldPrimaryKeyAttributes = '2.5.29.2'
    #...
    X509version3CertificateExtensionInhibitAny = '2.5.29.54'
}

#endregion Internals

#region Get-LabCertificate
function Get-LabCertificate
{
    # .ExternalHelp AutomatedLab.Help.xml
    [cmdletBinding(DefaultParameterSetName = 'Find')]
    param (
        [Parameter(Mandatory = $true, ParameterSetName = 'Find')]
        [string]$SearchString,

        [Parameter(Mandatory = $true, ParameterSetName = 'Find')]
        [System.Security.Cryptography.X509Certificates.X509FindType]$FindType,
        
        [System.Security.Cryptography.X509Certificates.CertStoreLocation]$Location,
        
        [System.Security.Cryptography.X509Certificates.StoreName]$Store,
        
        [string]$ServiceName,

        [Parameter(Mandatory = $true, ParameterSetName = 'All')]
        [switch]$All,

        [Parameter(ParameterSetName = 'All')]
        [switch]$IncludeServices,
        
        [string]$Password = 'AL',

        [Parameter(Mandatory)]
        [string[]]$ComputerName
    )
    
    Write-LogFunctionEntry
    
    $variables = Get-Variable -Name PSBoundParameters
    $functions = Get-Command -Name Get-Certificate2, Sync-Parameter

    $x = $PSBoundParameters
    
    foreach ($computer in $ComputerName)
    {
        Invoke-LabCommand -ActivityName 'Adding Cert Store Types' -ComputerName $ComputerName -ScriptBlock {
            Add-Type -TypeDefinition $args[0]
        } -ArgumentList $certStoreTypes -NoDisplay

        Invoke-LabCommand -ActivityName 'Exporting certificates' -ComputerName $ComputerName -ScriptBlock {
			$variables['Password']  = $variables['Password'] | ConvertTo-SecureString -AsPlainText -Force
            Sync-Parameter -Command (Get-Command -Name Get-Certificate2)
            Get-Certificate2 @ALBoundParameters
            
        } -Variable $variables -Function $functions -PassThru
    }
    
    Write-LogFunctionExit
}
#endregion Get-LabCertificate

#region Add-LabCertificate
function Add-LabCertificate
{
    # .ExternalHelp AutomatedLab.Help.xml
    [cmdletBinding(DefaultParameterSetName = 'ByteArray')]
    param(
        [Parameter(Mandatory = $true, ValueFromPipelineByPropertyName = $true, ParameterSetName = 'File')]
        [string]$Path,
        
        [Parameter(Mandatory = $true, ValueFromPipelineByPropertyName = $true, ParameterSetName = 'ByteArray')]
        [byte[]]$Cert,
        
        [Parameter(Mandatory = $true, ValueFromPipelineByPropertyName = $true)]
        [System.Security.Cryptography.X509Certificates.StoreName]$Store,
        
        [Parameter(Mandatory = $true, ValueFromPipelineByPropertyName = $true)]
        [System.Security.Cryptography.X509Certificates.CertStoreLocation]$Location,
        
        [Parameter(ValueFromPipelineByPropertyName = $true)]
        [string]$ServiceName,
        
        [Parameter(ValueFromPipelineByPropertyName = $true)]
        [ValidateSet('CER', 'PFX')]
        [string]$CertificateType = 'CER',
        
        [string]$Password = 'AL',

        [Parameter(Mandatory, ValueFromPipelineByPropertyName = $true)]
        [string[]]$ComputerName
    )
    
    begin
    {
        Write-LogFunctionEntry
    }
    
    process
    {
        $variables = Get-Variable -Name PSBoundParameters
        $functions = Get-Command -Name Add-Certificate2, Sync-Parameter

        Invoke-LabCommand -ActivityName 'Adding Cert Store Types' -ComputerName $ComputerName -ScriptBlock {
            Add-Type -TypeDefinition $args[0]
        } -ArgumentList $certStoreTypes -NoDisplay
        
        Invoke-LabCommand -ActivityName 'Storing certificate bytes on target machine' -ComputerName $ComputerName -ScriptBlock {
        
            $tempFile = [System.IO.Path]::GetTempFileName()
            [System.IO.File]::WriteAllBytes($tempFile, $args[0])
            Write-Verbose "Cert is written to '$tempFile'"
            
        } -ArgumentList (,$Cert) -Variable $variables
    
        Invoke-LabCommand -ActivityName 'Importing Cert file' -ComputerName $ComputerName -ScriptBlock {
			$variables['Password']  = $variables['Password'] | ConvertTo-SecureString -AsPlainText -Force
            Sync-Parameter -Command (Get-Command -Name Add-Certificate2)
            $ALBoundParameters.Add('Path', $tempFile)
            $ALBoundParameters.Remove('Cert')
            Add-Certificate2 @ALBoundParameters | Out-Null
            Remove-Item -Path $tempFile
            
        } -Variable $variables -Function $functions -PassThru
        
    }
    
    end
    {
        Write-LogFunctionExit
    }
}
#endregion Add-LabCertificate

# ...

#region Install-LabCAMachine
function Install-LabCAMachine
{
    # .ExternalHelp AutomatedLab.Help.xml
    [CmdletBinding()]
    
    param (
        [Parameter(Mandatory)]
        [AutomatedLab.Machine]$Machine,
        
        [int]$PreDelaySeconds,
        
        [switch]$PassThru
    )
    
    Write-LogFunctionEntry
    
    Write-Verbose -Message '****************************************************'
    Write-Verbose -Message "Starting installation of machine: $($machine.name)"
    Write-Verbose -Message '****************************************************'
    
    $role = $machine.Roles | Where-Object { $_.Name -eq ([AutomatedLab.Roles]::CaRoot) -or $_.Name -eq ([AutomatedLab.Roles]::CaSubordinate) }
    
    $param = [ordered]@{ }
    
    #region - Locate admin username and password for machine
    if ($machine.IsDomainJoined)
    {
        $domain = $lab.Domains | Where-Object { $_.Name -eq $machine.DomainName }
        
        $param.Add('UserName', ('{0}\{1}' -f $domain.Name, $domain.Administrator.UserName))
        $param.Add('Password', $domain.Administrator.Password)
        
        $rootDc = Get-LabMachine -Role RootDC | Where-Object DomainName -eq $machine.DomainName
        if ($rootDc) #if there is a root domain controller in the same domain as the machine
        {
            $rootDomain = (Get-Lab).Domains | Where-Object Name -eq $rootDc.DomainName
            $rootDomainNetBIOSName = ($rootDomain.Name -split '\.')[0]
        }
        else #else the machine is in a child domain and the parent domain need to be used for the query
        {
            $rootDomain = $lab.GetParentDomain($machine.DomainName)
            $rootDomainNetBIOSName = ($rootDomain.Name -split '\.')[0]
            $rootDc = Get-LabMachine -Role RootDC | Where-Object DomainName -eq $rootDomain
        }
        
        $param.Add('ForestAdminUserName', ('{0}\{1}' -f $rootDomainNetBIOSName, $rootDomain.Administrator.UserName))
        $param.Add('ForestAdminPassword', $rootDomain.Administrator.Password)
        
        Write-Debug -Message "Machine                   : $($machine.name)"
        Write-Debug -Message "Machine Domain            : $($machine.DomainName)"
        Write-Debug -Message "Username for job          : $($param.username)"
        Write-Debug -Message "Password for job          : $($param.Password)"
        Write-Debug -Message "ForestAdmin Username      : $($param.ForestAdminUserName)"
        Write-Debug -Message "ForestAdmin Password      : $($param.ForestAdminPassword)"
    }
    else
    {
        $param.Add('UserName', ('{0}\{1}' -f $machine.Name, $machine.InstallationUser.UserName))
        $param.Add('Password', $machine.InstallationUser.Password)
    }
    $param.Add('ComputerName', $Machine.Name)
    #endregion
    
    
    
    #region - Determine DNS name for machine. This is used when installing Enterprise CAs
    $caDNSName = $Machine.Name
    if ($Machine.DomainName) { $caDNSName += ('.' + $Machine.DomainName) }
    
    if ($Machine.DomainName)
    {
        $param.Add('DomainName', $Machine.DomainName)
    }
    else
    {
        $param.Add('DomainName', '')
    }
    
    
    if ($role.Name -eq 'CaSubordinate')
    {
        if (!($role.Properties.ContainsKey('ParentCA'))) { $param.Add('ParentCA', '<auto>') }
        else { $param.Add('ParentCA', $role.Properties.ParentCA) }
        if (!($role.Properties.ContainsKey('ParentCALogicalName'))) { $param.Add('ParentCALogicalName', '<auto>') }
        else { $param.Add('ParentCALogicalName', $role.Properties.ParentCALogicalName) }
    }
    
    #...
    
    if (!($role.Properties.ContainsKey('CPSURL'))) { $param.Add('CPSURL', 'http://' + $caDNSName + '/cps/cps.html') }
    else { $param.Add('CPSURL', $role.Properties.CPSURL) }
    if (!($role.Properties.ContainsKey('CPSText'))) { $param.Add('CPSText', 'Certification Practice Statement') }
    else { $param.Add('CPSText', $($role.Properties.CPSText)) }
    
    if (!($role.Properties.ContainsKey('InstallOCSP'))) { $param.Add('InstallOCSP', '<auto>') }
    else { $param.Add('InstallOCSP', ($role.Properties.InstallOCSP -like '*Y*')) }
    if (!($role.Properties.ContainsKey('OCSPHTTPURL01'))) { $param.Add('OCSPHTTPURL01', '<auto>') }
    else { $param.Add('OCSPHTTPURL01', $role.Properties.OCSPHTTPURL01) }
    if (!($role.Properties.ContainsKey('OCSPHTTPURL02'))) { $param.Add('OCSPHTTPURL02', '<auto>') }
    else { $param.Add('OCSPHTTPURL02', $role.Properties.OCSPHTTPURL02) }
    
    if (!($role.Properties.ContainsKey('DoNotLoadDefaultTemplates'))) { $param.Add('DoNotLoadDefaultTemplates', '<auto>') }
    else { $param.Add('DoNotLoadDefaultTemplates', $role.Properties.DoNotLoadDefaultTemplates -like '*Y*') }
    
    
    #region - Check if any unknown parameter name was passed
    $knownParameters = @()
    $knownParameters += 'ParentCA (only valid for Subordinate CA. Ignored for Root CAs)'
    $knownParameters += 'ParentCALogicalName (only valid for Subordinate CAs. Ignored for Root CAs)'
    $knownParameters += 'CACommonName'
    $knownParameters += 'CAType'
    #...
    $knownParameters += 'DoNotLoadDefaultTemplates'
    $knownParameters += 'PreDelaySeconds'
    $unkownParFound = $false
    foreach ($keySet in $role.Properties.GetEnumerator())
    {
        if ($keySet.Key -cnotin $knownParameters)
        {
            Write-Warning -Message "Parameter name '$($keySet.Key)' is unknown/ignored)"
            $unkownParFound = $true
        }
    }
    if ($unkownParFound)
    {
        Write-Warning -Message 'Valid parameter names are:'
        Foreach ($name in ($knownParameters.GetEnumerator()))
        {
            Write-Warning -Message "  $($name)"
        }
        Write-Warning -Message 'NOTE that all parameter names are CASE SENSITIVE!'
    }
    #endregion - Check if any unknown parameter names was passed
    
    #endregion - Parameters
    
    
    #region - Parameters debug
    Write-Debug -Message '---------------------------------------------------------------------------------------'
    Write-Debug -Message "Parameters for $($machine.name)"
    Write-Debug -Message '---------------------------------------------------------------------------------------'
    if ($machine.Roles.Properties.GetEnumerator().Count)
    {
        foreach ($r in $machine.Roles)
        {
            if (([AutomatedLab.Roles]$r.Name -band $roles) -ne 0) #if this is a CA role
            {
                foreach ($key in ($r.Properties.GetEnumerator() | Sort-Object -Property Key))
                {
                    Write-Debug -Message "  $($key.Key.PadRight(27)) $($key.Value)"
                }
            }
        }
    }
    else
    {
        Write-Debug -message '  No parameters specified'
    }
    Write-Debug -Message '---------------------------------------------------------------------------------------'
    #endregion - Parameters debug
    
    
    #region ----- Input validation (raw values) -----
    if ($role.Properties.ContainsKey('CACommonName') -and ($param.CACommonName.Length -gt 37))
    {
        Write-Error -Message "CACommonName cannot be longer than 37 characters. Specified value is: '$($param.CACommonName)'"; return
    }
    
    if ($role.Properties.ContainsKey('CACommonName') -and ($param.CACommonName.Length -lt 1))
    {
        Write-Error -Message "CACommonName cannot be blank. Specified value is: '$($param.CACommonName)'"; return
    }
    
    if ($role.Name -eq 'CaRoot')
    {
        if (-not ($param.CAType -in 'EnterpriseRootCA', 'StandAloneRootCA', '<auto>'))
        {
            Write-Error -Message "CAType needs to be 'EnterpriseRootCA' or 'StandAloneRootCA' when role is CaRoot. Specified value is: '$param.CAType'"; return
        }
    }
    
    if ($role.Name -eq 'CaSubordinate')
    {
        if (-not ($param.CAType -in 'EnterpriseSubordinateCA', 'StandAloneSubordinateCA', '<auto>'))
        {
            Write-Error -Message "CAType needs to be 'EnterpriseSubordinateCA' or 'StandAloneSubordinateCA' when role is CaSubordinate. Specified value is: '$param.CAType'"; return
        }
    }
    
    
    $availableCombinations = @()
    $availableCombinations += @{CryptoProviderName='Microsoft Base SMart Card Crypto Provider';           HashAlgorithmName='sha1','md2','md4','md5';                           KeyLength='1024','2048','4096'}
    #...    
    $combination = $availableCombinations | Where-Object {$_.CryptoProviderName -eq $param.CryptoProviderName}
    
    if (-not ($param.CryptoProviderName -in $combination.CryptoProviderName))
    {
        Write-Error -Message "CryptoProviderName '$($param.CryptoProviderName)' is unknown. `nList of valid options for CryptoProviderName:`n  $($availableCombinations.CryptoProviderName -join "`n  ")"; return
    }
    elseif (-not ($param.HashAlgorithmName -in $combination.HashAlgorithmName))
    {
        Write-Error -Message "HashAlgorithmName '$($param.HashAlgorithmName)' is not valid for CryptoProviderName '$($param.CryptoProviderName)'. The Crypto Provider selected supports the following Hash Algorithms:`n  $($combination.HashAlgorithmName -join "`n  ")"; return
    }
    elseif (-not ($param.KeyLength -in $combination.KeyLength))
    {
        Write-Error -Message "Keylength '$($param.KeyLength)' is not valid for CryptoProviderName '$($param.CryptoProviderName)'. The Crypto Provider selected supports the following keylengths:`n  $($combination.KeyLength -join "`n  ")"; return
    }

    

    if ($role.Properties.ContainsKey('DatabaseDirectory') -and -not ($param.DatabaseDirectory -match '^[C-Z]:\\'))
    {
        Write-Error -Message 'DatabaseDirectory needs to be located on a local drive (drive letter C-Z)'; return
    }
    
    #...    
    
    #if any validity parameter was defined, get these now and convert them all to hours (temporary variables)
    if ($param.ValidityPeriodUnits -ne '<auto>')
    {
        switch ($param.ValidityPeriod)
        {
            'Years'  { $validityPeriodUnitsHours = [int]$param.ValidityPeriodUnits * 365 * 24 }
            'Months' { $validityPeriodUnitsHours = [int]$param.ValidityPeriodUnits * (365/12) * 24 }
            'Weeks'  { $validityPeriodUnitsHours = [int]$param.ValidityPeriodUnits * 7 * 24 }
            'Days'   { $validityPeriodUnitsHours = [int]$param.ValidityPeriodUnits * 24 }
            'Hours'  { $validityPeriodUnitsHours = [int]$param.ValidityPeriodUnits }
        }
    }
    if ($param.CertsValidityPeriodUnits -ne '<auto>')
    {
        switch ($param.CertsValidityPeriod)
        {
            'Years'  { $certsvalidityPeriodUnitsHours = [int]$param.CertsValidityPeriodUnits * 365 * 24 }
            'Months' { $certsvalidityPeriodUnitsHours = [int]$param.CertsValidityPeriodUnits * (365/12) * 24 }
            'Weeks'  { $certsvalidityPeriodUnitsHours = [int]$param.CertsValidityPeriodUnits * 7 * 24 }
            'Days'   { $certsvalidityPeriodUnitsHours = [int]$param.CertsValidityPeriodUnits * 24 }
            'Hours'  { $certsvalidityPeriodUnitsHours = [int]$param.CertsValidityPeriodUnits }
        }
    }
    if ($param.CRLPeriodUnits -ne '<auto>')
    {
        switch ($param.CRLPeriod)
        {
            'Years'  { $cRLPeriodUnitsHours = [int]([int]$param.CRLPeriodUnits * 365 * 24) }
            'Months' { $cRLPeriodUnitsHours = [int]([int]$param.CRLPeriodUnit * (365/12) * 24) }
            'Weeks'  { $cRLPeriodUnitsHours = [int]([int]$param.CRLPeriodUnits * 7 * 24) }
            'Days'   { $cRLPeriodUnitsHours = [int]([int]$param.CRLPeriodUnits * 24) }
            'Hours'  { $cRLPeriodUnitsHours = [int]([int]$param.CRLPeriodUnits) }
        }
    }
    if ($param.CRLDeltaPeriodUnits -ne '<auto>')
    {
        switch ($param.CRLDeltaPeriod)
        {
            'Years'  { $cRLDeltaPeriodUnitsHours = [int]([int]$param.CRLDeltaPeriodUnits * 365 * 24) }
            'Months' { $cRLDeltaPeriodUnitsHours = [int]([int]$param.CRLDeltaPeriodUnits * (365/12) * 24) }
            'Weeks'  { $cRLDeltaPeriodUnitsHours = [int]([int]$param.CRLDeltaPeriodUnits * 7 * 24) }
            'Days'   { $cRLDeltaPeriodUnitsHours = [int]([int]$param.CRLDeltaPeriodUnits * 24) }
            'Hours'  { $cRLDeltaPeriodUnitsHours = [int]([int]$param.CRLDeltaPeriodUnits) }
        }
    }
    if ($param.CRLOverlapUnits -ne '<auto>')
    {
        switch ($param.CRLOverlapPeriod)
        {
            'Years'  { $CRLOverlapUnitsHours = [int]([int]$param.CRLOverlapUnits * 365 * 24) }
            'Months' { $CRLOverlapUnitsHours = [int]([int]$param.CRLOverlapUnits * (365/12) * 24) }
            'Weeks'  { $CRLOverlapUnitsHours = [int]([int]$param.CRLOverlapUnits * 7 * 24) }
            'Days'   { $CRLOverlapUnitsHours = [int]([int]$param.CRLOverlapUnits * 24) }
            'Hours'  { $CRLOverlapUnitsHours = [int]([int]${param}.CRLOverlapUnits) }
        }
    }
    #...
}
#endregion Install-LabCAMachine
#...
<#http://example.com.#>
Write-Debug `$`{`}
Write-Debug $false.$true.$param
$(Write-Debug $true)

:OuterLoop while ($true) {
    while ($true) { break OuterLoop }
}
